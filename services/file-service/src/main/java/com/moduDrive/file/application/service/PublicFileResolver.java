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
 * Google-Drive-style stable link {@code /public/{fileId}}. {@code fileId} identifies the entry;
 * access is granted through one of two independent means:
 * <ul>
 *   <li>{@code fileId} alone, when the entry (or an ancestor folder) is scope LINK — see
 *       {@link FileAccessGuard#linkRole}. No {@code key} needed: {@code fileId} is already an
 *       unguessable capability (issue #303), and this opens that entry <b>and everything nested
 *       under it</b>;</li>
 *   <li>failing that, {@code key} against a pending/claimed guest share's per-invite
 *       {@code token} (see {@link FileShare#createPending}) — opens <b>only the one entry it was
 *       minted for</b>, never a subtree or a directory listing.</li>
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
     * a guest invite minted for this exact entry. */
    File resolve(String fileId, String key) {
        File target = target(fileId);
        if (fileAccessGuard.linkRole(target) != null || matchesGuestInvite(target, key)) {
            return target;
        }
        throw notFound();
    }

    /** Direct children of the directory at {@code fileId} (a link-shared folder, or one nested
     * under it), trashed/purged entries excluded. A per-invite guest token can never reach this —
     * listing a folder needs the folder itself (or an ancestor) to be "anyone with the link". */
    List<File> resolveChildren(String fileId) {
        File dir = target(fileId);
        if (!dir.isDirectory() || fileAccessGuard.linkRole(dir) == null) {
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

    /** True when {@code key} is a live guest invite minted for this exact entry — never a
     * subtree, unlike a LINK-scoped folder's reach. */
    private boolean matchesGuestInvite(File target, String key) {
        return parseUuid(key)
                .flatMap(findFileSharePort::findByToken)
                .filter(share -> share.getFileId().equals(target.getId()))
                .isPresent();
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
