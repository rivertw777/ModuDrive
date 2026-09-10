package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The single place that answers "may this caller act on this file?". Ownership is
 * {@code file.ownerId}, checked directly — there is no OWNER role or share row, and the owner
 * implicitly holds every permission.
 * <p>
 * A share on a directory is inherited by everything nested under it (Google-Drive-style): the
 * grant is stored only on the directory, and access to a descendant is resolved at check time by
 * walking the descendant's ancestor directories. Nothing is copied onto child rows.
 */
@Component
@RequiredArgsConstructor
class FileAccessGuard {

    private final FindFileSharePort findFileSharePort;
    private final FindFilePort findFilePort;

    /** The caller's effective role on this file — their own grant on this exact file if they have
     * one (an ancestor's is never consulted then, however more generous it is), otherwise the
     * nearest ancestor directory's grant above it. Null when they own it or have no grant at
     * all. */
    Role effectiveRole(File file, UUID callerId) {
        if (isOwner(file, callerId)) {
            return null;
        }
        return resolveRole(file, callerId);
    }

    /** What a descendant of {@code directory} inherits by virtue of directory access alone. From a
     * descendant's point of view, {@code directory} is just its nearest ancestor — the same rule
     * {@code resolveRole} applies to a file, so the body is identical; this is a separate,
     * package-visible method purely to name the caller's intent (a descendant's *own* direct
     * grant, if it has one, is layered on top by the caller — see {@code ListSharedDirectoryService}
     * — the same way {@code resolveRole} layers a file's own grant over its ancestors). Null when
     * the caller has no grant on {@code directory} or above. */
    Role inheritableRole(File directory, UUID callerId) {
        return resolveRole(directory, callerId);
    }

    void requireOwner(File file, UUID callerId) {
        if (!isOwner(file, callerId)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    void requirePermission(File file, UUID callerId, Permission required) {
        if (isOwner(file, callerId)) {
            return;
        }
        // A trashed or purged item is owner-only (restore / purge). To a grantee it's gone — an
        // inherited grant from a still-live ancestor directory must not keep a removed descendant
        // readable or downloadable. The owner short-circuits above, so their own
        // restore/purge/FILE_ALREADY_DELETED paths are untouched.
        if (file.isRemoved()) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
        Role granted = resolveRole(file, callerId);
        if (granted == null || !granted.permissions().contains(required)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    /** Returns null when the caller has no explicit share on this file or on any directory above
     * it, and neither this file nor any ancestor is LINK-scoped either. A named grant (this
     * file's own, or failing that the nearest ancestor's) is authoritative and always outranks a
     * LINK fallback, even a more generous one — same priority as {@link #resolveGrant} below. Only
     * once no named grant exists anywhere on the path does a self/ancestor LINK scope kick in,
     * granting a signed-in stranger the same viewer-only access an anonymous link-holder already
     * gets through the public routes (issue #303) — there is no reason to make a logged-in visitor
     * use a different, token-bearing URL for the same "anyone with the link" file. */
    private Role resolveRole(File file, UUID callerId) {
        if (callerId == null) {
            return null;
        }
        // A grant on this exact file is authoritative and skips ancestors entirely — it's a
        // deliberate, file-specific decision by the owner, so it overrides an inherited grant
        // even when the inherited one would be more generous (same priority as resolveGrant
        // below, which likewise returns the file's own share row before considering any
        // ancestor's). Only when there's no direct grant do ancestors get consulted, nearest-wins.
        Role own = grantedRole(file.getId(), callerId);
        if (own != null) {
            return own;
        }
        List<File> ancestors = ancestorDirectories(file);
        Role inherited = foldAncestors(ancestors, callerId);
        if (inherited != null) {
            return inherited;
        }
        return linkRoleFallback(file, ancestors);
    }

    /** The nearest ancestor's grant, not the most generous one across all ancestors — a farther
     * ancestor's more generous role never outranks a nearer ancestor's, matching
     * {@link #resolveGrant}'s own nearest-wins walk. */
    private Role foldAncestors(List<File> ancestors, UUID callerId) {
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            Role role = grantedRole(ancestors.get(i).getId(), callerId);
            if (role != null) {
                return role;
            }
        }
        return null;
    }

    /** Last resort once no named grant exists anywhere on the path: this file's own LINK scope,
     * or failing that, the nearest ancestor directory's. Either way the granted role is always
     * VIEWER (link sharing never hands out more, see {@code UpdateFileScopeService}), so which
     * one matched doesn't change the outcome — no nearest-wins tie-break needed here. */
    private Role linkRoleFallback(File file, List<File> ancestors) {
        if (file.getAccessScope() == ShareScope.LINK) {
            return file.getLinkRole();
        }
        for (File ancestor : ancestors) {
            if (ancestor.getAccessScope() == ShareScope.LINK) {
                return ancestor.getLinkRole();
            }
        }
        return null;
    }

    /** The specific share row that explains why {@code callerId} can read {@code file} — their
     * own grant on it, or failing that, the nearest ancestor directory's. Unlike
     * {@link #effectiveRole} (which only needs the resolved role, for a permission check),
     * a listing that shows "공유한 사용자"/"공유된 날짜" for a shared directory's contents needs the
     * actual origin grant, so every child in the listing attributes to the same one. Empty when
     * the caller owns the file or holds no grant on it or any ancestor. */
    Optional<FileShare> resolveGrant(File file, UUID callerId) {
        if (callerId == null) {
            return Optional.empty();
        }
        Optional<FileShare> own = findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(file.getId()), callerId);
        if (own.isPresent()) {
            return own;
        }
        // ancestorDirectories() returns root-most first; walk it backwards so the first hit is
        // the nearest ancestor, matching ListFileSharesService's own nearest-wins tie-break.
        List<File> ancestors = ancestorDirectories(file);
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            Optional<FileShare> grant =
                    findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(ancestors.get(i).getId()), callerId);
            if (grant.isPresent()) {
                return grant;
            }
        }
        return Optional.empty();
    }

    private Role grantedRole(UUID fileId, UUID callerId) {
        return findFileSharePort
                .findByFileIdAndSharedWithUserId(new FileId(fileId), callerId)
                .map(FileShare::getRole)
                .orElse(null);
    }

    /** The directories on {@code file}'s materialized path, root-most first. {@code file.path} is
     * the parent path, so each segment names one ancestor directory.
     * ponytail: one lookup per path segment (depth-bounded, typically &lt;5) — add an
     * effective-ACL cache only if deep trees or read volume make it hurt. */
    List<File> ancestorDirectories(File file) {
        List<File> ancestors = new ArrayList<>();
        String parentPath = file.getPath();
        if (parentPath == null || "/".equals(parentPath)) {
            return ancestors;
        }
        NamespaceId namespaceId = new NamespaceId(file.getNamespaceId());
        String walked = "/";
        for (String name : parentPath.substring(1).split("/")) {
            findFilePort.findActiveByNamespaceIdAndPathAndName(namespaceId, walked, name)
                    .filter(File::isDirectory)
                    .ifPresent(ancestors::add);
            walked = "/".equals(walked) ? "/" + name : walked + "/" + name;
        }
        return ancestors;
    }

    private boolean isOwner(File file, UUID callerId) {
        return callerId != null && callerId.equals(file.getOwnerId());
    }
}
