package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a {@code (fileId, key)} pair into a file for the unauthenticated routes — the
 * Google-Drive-style stable link {@code /files/{fileId}} (spec 3; {@code /public/{fileId}} is a
 * legacy redirect alias, not the address itself). {@code fileId} identifies the entry;
 * access is granted through one of two independent means:
 * <ul>
 *   <li>{@code fileId} alone, when the entry (or an ancestor folder) is scope LINK — see
 *       {@link FileAccessGuard#linkRole}. No {@code key} needed: {@code fileId} is already an
 *       unguessable capability (issue #303), and this opens that entry <b>and everything nested
 *       under it</b>;</li>
 *   <li>failing that, {@code key} against a pending/claimed guest share's per-invite
 *       {@code token} (see {@link FileShare#createPending}) — opens the entry it was minted for
 *       <b>and, when that entry is a folder, everything nested under it</b>, the same inheritance
 *       a signed-in grantee gets (folder sharing wouldn't otherwise let an invited guest browse
 *       the folder they were actually invited to).</li>
 * </ul>
 * Every rejection is the same FILE_NOT_FOUND regardless of which check almost passed: an
 * anonymous caller must not be able to tell "malformed" from "wrong key" from "right key, sharing
 * switched off" from "right key, file trashed" from "right key, wrong fileId".
 * <p>
 * Shared by every public route so the metadata and the download paths can never disagree about
 * which links are live.
 */
@Component
@RequiredArgsConstructor
class PublicFileResolver {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final FileAccessGuard fileAccessGuard;

    /** The entry at {@code fileId}, provided either it (or an ancestor) is plain "anyone with the
     * link" (no {@code key} needed, see {@link FileAccessGuard#linkRole}), or {@code key} matches
     * a guest invite minted for this entry or a directory above it (see {@link
     * #matchesGuestInvite}). */
    File resolve(String fileId, String key) {
        File target = target(fileId);
        List<File> ancestors = fileAccessGuard.ancestorDirectories(target);
        if (fileAccessGuard.linkRole(target, ancestors) != null || matchesGuestInvite(target, key, ancestors)) {
            return target;
        }
        throw notFound();
    }

    /** Direct children of the directory at {@code fileId} (a link-shared folder, or one nested
     * under it, or one reachable through a guest invite on itself or an ancestor — see {@link
     * #matchesGuestInvite}), trashed/purged entries excluded. */
    List<File> resolveChildren(String fileId, String key) {
        File dir = target(fileId);
        // isDirectory() first, still short-circuiting before any FileAccessGuard call: a public
        // file id used against this route is never going to authorize anything here anyway, so
        // there's nothing for the ancestor walk below to add.
        if (!dir.isDirectory()) {
            throw notFound();
        }
        List<File> ancestors = fileAccessGuard.ancestorDirectories(dir);
        if (fileAccessGuard.linkRole(dir, ancestors) == null && !matchesGuestInvite(dir, key, ancestors)) {
            throw notFound();
        }
        return findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(dir.getNamespaceId()), dir.fullPath())
                .stream()
                .filter(f -> !f.isRemoved())
                .toList();
    }

    private File target(String fileId) {
        return parseUuid(fileId)
                .flatMap(id -> findFilePort.findById(new FileId(id)))
                .filter(file -> !file.isRemoved())
                .orElseThrow(this::notFound);
    }

    /** True when {@code key} is a live guest invite minted for this exact entry, or for a
     * directory somewhere above it — a folder invite reaches its whole subtree, the same
     * inheritance {@code ancestors} gives a signed-in grantee. Takes the caller's already-computed
     * ancestor list rather than recomputing it: {@code linkRole} just walked the same path, and
     * every rejection here answers the same FILE_NOT_FOUND either way, so doing the walk twice
     * would only cost a query and widen the timing gap between rejection reasons for nothing. */
    private boolean matchesGuestInvite(File target, String key, List<File> ancestors) {
        return parseUuid(key)
                .flatMap(findFileSharePort::findByToken)
                .filter(share -> mintedForTargetOrAnAncestor(share, target, ancestors))
                .isPresent();
    }

    private boolean mintedForTargetOrAnAncestor(FileShare share, File target, List<File> ancestors) {
        if (share.getFileId().equals(target.getId())) {
            return true;
        }
        return ancestors.stream().anyMatch(ancestor -> share.getFileId().equals(ancestor.getId()));
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private BusinessException notFound() {
        return new BusinessException(FileExceptionCase.FILE_NOT_FOUND);
    }
}
