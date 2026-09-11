package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.in.usecase.RevokeFileShareUseCase;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@UseCase
@RequiredArgsConstructor
class RevokeFileShareService implements RevokeFileShareUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final DeleteFileSharePort deleteFileSharePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public void revokeFileShare(RevokeFileShareCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        Optional<FileShare> maybeShare = findFileSharePort.findByShareId(command.getShareId());
        if (maybeShare.isEmpty()) {
            // Already gone — most often this file's own ancestor cascade below finishing the job
            // before a second, redundant revoke for the same person arrives (a client that fires
            // the direct and ancestor revokes concurrently, rather than waiting for each to land),
            // or a retry of a call that in fact already succeeded. The caller already owns this
            // file (checked above) and their desired end state — this share doesn't exist — is
            // already true, so treat a re-request as success instead of an error nobody can act on.
            return;
        }

        // A share id from another file must not be revocable by this file's owner.
        FileShare fileShare = maybeShare
                .filter(share -> share.getFileId().equals(command.getFileId().value()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND));

        deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(fileShare.getId()));
        revokeAncestorGrants(file, fileShare);
    }

    /** Deleting only the file's own grant would <em>promote</em> the grantee, not remove them:
     * {@code FileAccessGuard} treats a direct grant as authoritative and consults ancestors only
     * when there is none, so removing the direct row uncovers whatever the ancestor directory
     * still hands out — frequently a more generous role than the one just revoked. "Stop sharing
     * this with them" therefore has to reach the whole path above the file as well, which is what
     * the spec's 공유 권한 삭제 rule asks for. Every ancestor is swept, not just the nearest, since
     * any one of them left behind re-grants access on its own. */
    private void revokeAncestorGrants(File file, FileShare revoked) {
        for (File ancestor : fileAccessGuard.ancestorDirectories(file)) {
            findGranteeShare(new FileId(ancestor.getId()), revoked)
                    .ifPresent(grant -> deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(grant.getId())));
        }
    }

    /** The same grantee's share on another file, tried both ways: a claimed member's ancestor
     * grant is found by id first, but the same person can still have an unclaimed, email-only
     * invite sitting on an ancestor (see {@code ClaimPendingFileSharesService}) — matching by id
     * alone would walk straight past it and leave that ancestor still handing out access. The
     * email for that lookup can't come from {@code revoked} itself: {@link FileShare#claim}
     * always clears {@code granteeEmail} once a row is claimed, so a member-id row never carries
     * one (issue #323) — it has to be resolved via member-service instead, and only when the id
     * lookup actually misses, so a normal revoke with no ancestor invite never pays for it. */
    private Optional<FileShare> findGranteeShare(FileId ancestorId, FileShare revoked) {
        UUID granteeId = revoked.getSharedWithUserId();
        if (granteeId != null) {
            Optional<FileShare> byUserId = findFileSharePort.findByFileIdAndSharedWithUserId(ancestorId, granteeId);
            if (byUserId.isPresent()) {
                return byUserId;
            }
            return resolveEmail(granteeId)
                    .flatMap(email -> findFileSharePort.findByFileIdAndGranteeEmail(ancestorId, email));
        }
        if (revoked.getGranteeEmail() != null) {
            return findFileSharePort.findByFileIdAndGranteeEmail(ancestorId, revoked.getGranteeEmail());
        }
        return Optional.empty();
    }

    /** Best-effort: a member-service hiccup must not block the revoke itself, only the
     * email-based half of the ancestor sweep above — same degrade-on-failure pattern as
     * {@code ShareFileService.resolveGranter}. */
    private Optional<String> resolveEmail(UUID memberId) {
        try {
            return Optional.ofNullable(findMemberByIdPort.findMemberById(memberId).email());
        } catch (RuntimeException e) {
            log.warn("Could not resolve email for {} while sweeping ancestor grants for revoke", memberId, e);
            return Optional.empty();
        }
    }
}
