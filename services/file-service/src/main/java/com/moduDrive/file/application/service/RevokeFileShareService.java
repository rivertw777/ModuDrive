package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.in.usecase.RevokeFileShareUseCase;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@UseCase
@RequiredArgsConstructor
class RevokeFileShareService implements RevokeFileShareUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final DeleteFileSharePort deleteFileSharePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public void revokeFileShare(RevokeFileShareCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        // A share id from another file must not be revocable by this file's owner.
        FileShare fileShare = findFileSharePort.findByShareId(command.getShareId())
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

    /** The same grantee's share on another file. A claimed share points at a member id; a guest
     * invite still waiting to be claimed carries only the invited email (see
     * {@link FileShare#createPending}) — match on whichever identity this row actually has, so a
     * pending guest's ancestor invites are revoked alongside a registered member's. */
    private Optional<FileShare> findGranteeShare(FileId ancestorId, FileShare revoked) {
        if (revoked.getSharedWithUserId() != null) {
            return findFileSharePort.findByFileIdAndSharedWithUserId(ancestorId, revoked.getSharedWithUserId());
        }
        if (revoked.getGranteeEmail() != null) {
            return findFileSharePort.findByFileIdAndGranteeEmail(ancestorId, revoked.getGranteeEmail());
        }
        return Optional.empty();
    }
}
