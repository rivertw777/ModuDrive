package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RevokeFileShareServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private DeleteFileSharePort deleteFileSharePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private RevokeFileShareService revokeFileShareService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID granteeId = UUID.randomUUID();
    private final RevokeFileShareCommand command = new RevokeFileShareCommand(fileId, shareId, ownerId);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    private FileShare share(UUID belongsToFileId) {
        return share(belongsToFileId, Role.VIEWER);
    }

    private FileShare share(UUID belongsToFileId, Role role) {
        return share(belongsToFileId, role, granteeId);
    }

    private FileShare share(UUID belongsToFileId, Role role, UUID grantee) {
        return FileShare.withId(new FileShareId(shareId), new FileShareFileId(belongsToFileId),
                new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(grantee),
                new FileShareRole(role));
    }

    @Nested
    @DisplayName("소유자가 자기 파일의 공유를 해제할 때")
    class WhenOwnerRevokesOwnFileShare {

        @Test
        void deletesShare() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of());

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(shareId));
        }
    }

    @Nested
    @DisplayName("같은 사람이 조상 폴더에도 공유를 가지고 있을 때")
    class WhenTheSameGranteeAlsoHasAnAncestorGrant {

        private File directory(String name, String path) {
            return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName(name), new FilePath(path), new FileOwnerId(ownerId),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
        }

        private FileShare grantOn(UUID onFileId, UUID grantShareId, Role role) {
            return FileShare.withId(new FileShareId(grantShareId), new FileShareFileId(onFileId),
                    new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(granteeId),
                    new FileShareRole(role));
        }

        @Test
        void deletesTheAncestorGrantTooSoTheGranteeIsNotPromoted() {
            // The whole point of issue #310: dropping only the direct VIEWER grant would uncover
            // the parent's EDITOR one, handing the grantee *more* access than before the revoke.
            File parent = directory("projects", "/");
            UUID parentShareId = UUID.randomUUID();
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId()))
                    .willReturn(Optional.of(share(fileId, Role.VIEWER)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.of(grantOn(parent.getId(), parentShareId, Role.EDITOR)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(shareId));
            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(parentShareId));
        }

        @Test
        void deletesEveryAncestorGrantNotJustTheNearest() {
            // A single surviving ancestor re-grants access on its own, so the walk can't stop at
            // the first hit the way FileAccessGuard's nearest-wins lookup does.
            File grandParent = directory("work", "/");
            File parent = directory("projects", "/work");
            UUID grandParentShareId = UUID.randomUUID();
            UUID parentShareId = UUID.randomUUID();
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(grandParent, parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(grandParent.getId()), granteeId))
                    .willReturn(Optional.of(grantOn(grandParent.getId(), grandParentShareId, Role.VIEWER)));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.of(grantOn(parent.getId(), parentShareId, Role.EDITOR)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(grandParentShareId));
            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(parentShareId));
        }

        @Test
        void deletesAPendingGuestsAncestorInviteByEmail() {
            // An unclaimed guest invite has no member id at all — only the invited email identifies
            // them, so the ancestor lookup has to switch to that column or the guest keeps access.
            File parent = directory("projects", "/");
            UUID parentShareId = UUID.randomUUID();
            FileShare pendingGuestShare = FileShare.withId(new FileShareId(shareId),
                    new FileShareFileId(fileId), new FileShareOwnerId(ownerId), null,
                    new FileShareRole(Role.VIEWER), UUID.randomUUID(), "guest@example.com", null);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(pendingGuestShare));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndGranteeEmail(new FileId(parent.getId()), "guest@example.com"))
                    .willReturn(Optional.of(FileShare.withId(new FileShareId(parentShareId),
                            new FileShareFileId(parent.getId()), new FileShareOwnerId(ownerId), null,
                            new FileShareRole(Role.EDITOR), UUID.randomUUID(), "guest@example.com", null)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(parentShareId));
        }

        @Test
        void deletesNothingExtraWhenNoAncestorGrantsThemAnything() {
            File parent = directory("projects", "/");
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(fileAccessGuard.ancestorDirectories(file)).willReturn(List.of(parent));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(parent.getId()), granteeId))
                    .willReturn(Optional.empty());

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should(times(1)).deleteFileShare(any(FileShareId.class));
        }
    }

    @Nested
    @DisplayName("공유가 다른 파일에 속할 때")
    class WhenShareBelongsToAnotherFile {

        @Test
        void throwsFileShareNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId()))
                    .willReturn(Optional.of(share(UUID.randomUUID())));

            Throwable thrown = catchThrowable(() -> revokeFileShareService.revokeFileShare(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_NOT_FOUND);
            then(deleteFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자가 파일 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(any(File.class), eq(ownerId));

            Throwable thrown = catchThrowable(() -> revokeFileShareService.revokeFileShare(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(deleteFileSharePort).shouldHaveNoInteractions();
        }
    }
}
