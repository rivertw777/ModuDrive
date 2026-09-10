package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
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

@ExtendWith(MockitoExtension.class)
class PublicFileResolverTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private PublicFileResolver publicFileResolver;

    private final UUID key = UUID.randomUUID();
    private final UUID namespaceId = UUID.randomUUID();

    private File file(String name, String path, boolean directory, FileStatus status) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId),
                new FileName(name), new FilePath(path), new FileOwnerId(UUID.randomUUID()),
                null, null, status, new FileIsDirectory(directory));
    }

    private void assertNotFound(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
    }

    private void givenFound(File f) {
        given(findFilePort.findById(new FileId(f.getId()))).willReturn(Optional.of(f));
    }

    private FileShare guestShareOn(File f) {
        return FileShare.createPending(new FileShareFileId(f.getId()), new FileShareOwnerId(f.getOwnerId()),
                new FileShareGranteeEmail("guest@example.com"), new FileShareRole(Role.VIEWER));
    }

    @Nested
    @DisplayName("key 없이, fileId만으로 scope==LINK에 도달할 때 (issue #303)")
    class WhenLinkScopeReachesWithoutAKey {

        @Test
        @DisplayName("파일 자신이 LINK scope면 key가 없어도 열린다")
        void ownLinkScopeNeedsNoKey() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);
            given(fileAccessGuard.linkRole(f)).willReturn(Role.VIEWER);

            assertThat(publicFileResolver.resolve(f.getId().toString(), null).getName())
                    .isEqualTo("report.pdf");
        }

        @Test
        @DisplayName("조상 폴더가 LINK scope면 key가 없어도(엉뚱한 key여도) 열린다")
        void ancestorLinkScopeNeedsNoKeyEitherAndIgnoresAGarbageKey() {
            File f = file("report.pdf", "/shared", false, FileStatus.UPLOADED);
            givenFound(f);
            given(fileAccessGuard.linkRole(f)).willReturn(Role.VIEWER);

            assertThat(publicFileResolver.resolve(f.getId().toString(), "not-a-uuid").getName())
                    .isEqualTo("report.pdf");
            // The key-based path (findByLinkToken/findByToken) is never even consulted once the
            // fileAccessGuard fast path already succeeded.
            then(findFileSharePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("디렉토리 자신이 LINK scope면 key 없이도 자식 목록을 조회할 수 있다")
        void resolvesChildrenOfALinkScopedDirectoryWithoutAKey() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            givenFound(folder);
            given(fileAccessGuard.linkRole(folder)).willReturn(Role.VIEWER);
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            given(findFilePort.findByNamespaceIdAndPath(any(), eq("/shared"))).willReturn(List.of(child));

            assertThat(publicFileResolver.resolveChildren(folder.getId().toString(), null))
                    .containsExactly(child);
        }

        @Test
        @DisplayName("LINK scope여도 파일(디렉토리 아님)에는 자식 목록 조회가 안 된다")
        void nonDirectoryStillRejectedForResolveChildrenEvenWhenLinkScoped() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(f);

            assertNotFound(catchThrowable(() -> publicFileResolver.resolveChildren(f.getId().toString(), null)));
        }
    }

    @Nested
    @DisplayName("key가 파일 자신의 LINK 토큰일 때")
    class WhenKeyIsTheFilesOwnLinkToken {

        @Test
        void returnsThatFile() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            f.enableLinkSharing(key, Role.VIEWER);
            givenFound(f);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(f));

            assertThat(publicFileResolver.resolve(f.getId().toString(), key.toString()).getName())
                    .isEqualTo("report.pdf");
        }

        @Test
        void resolvesEditorLinksToo() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            f.enableLinkSharing(key, Role.EDITOR);
            givenFound(f);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(f));

            assertThat(publicFileResolver.resolve(f.getId().toString(), key.toString()).getLinkRole())
                    .isEqualTo(Role.EDITOR);
        }
    }

    @Nested
    @DisplayName("파일의 공유 범위가 RESTRICTED로 돌아갔을 때")
    class WhenScopeIsRestricted {

        @Test
        void throwsFileNotFoundWithoutLeakingThatKeyMatched() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED); // no enableLinkSharing
            givenFound(f);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(f));
            given(findFileSharePort.findByToken(key)).willReturn(Optional.empty());

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), key.toString())));
        }
    }

    @Nested
    @DisplayName("key가 게스트 초대(pending share) 토큰일 때")
    class WhenKeyIsAGuestShareToken {

        @Test
        void returnsTheFileEvenThoughItsScopeStaysRestricted() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.empty());
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(f)));
            givenFound(f);

            assertThat(publicFileResolver.resolve(f.getId().toString(), key.toString()).getName())
                    .isEqualTo("report.pdf");
        }

        @Test
        @DisplayName("게스트 토큰은 초대된 항목만 열 뿐 하위 트리는 열지 못한다")
        void doesNotUnlockADescendantOfAGuestSharedFolder() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.empty());
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(folder)));
            givenFound(folder);
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            givenFound(child);

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(child.getId().toString(), key.toString())));
        }

        @Test
        @DisplayName("게스트 토큰으로는 폴더 목록 조회 자체가 불가")
        void cannotListChildrenWithAGuestToken() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.empty());
            given(findFileSharePort.findByToken(key)).willReturn(Optional.of(guestShareOn(folder)));
            givenFound(folder);

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolveChildren(folder.getId().toString(), key.toString())));
        }
    }

    @Nested
    @DisplayName("공개된 파일이 휴지통으로 갔을 때")
    class WhenFileIsDeleted {

        @Test
        void throwsFileNotFoundWhenTheTargetIsTrashed() {
            // target() itself filters out anything removed before any key/scope check even runs,
            // so no link/guest setup is needed here at all — a trashed target is unreachable no
            // matter how it would otherwise have been unlocked.
            File f = file("report.pdf", "/1", false, FileStatus.TRASHED);
            givenFound(f);

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), key.toString())));
        }

        @Test
        @DisplayName("자손은 살아있어도 링크 공유된 루트 폴더가 휴지통이면 거부")
        void throwsWhenTheLinkSharedRootFolderIsTrashed() {
            File folder = file("shared", "/", true, FileStatus.TRASHED);
            folder.enableLinkSharing(key, Role.VIEWER);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            given(findFileSharePort.findByToken(key)).willReturn(Optional.empty());
            // The child itself is live and resolves fine; resolve() only rejects once it walks up
            // to unlockRoot and finds the link-shared root trashed.
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            givenFound(child);

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(child.getId().toString(), key.toString())));
        }

        @Test
        @DisplayName("파일은 살아있어도 게스트 공유된 루트가 휴지통이면 거부")
        void throwsWhenTheGuestSharedRootIsTrashed() {
            // Same as throwsFileNotFoundWhenTheTargetIsTrashed above — target() filters the
            // trashed target out before any guest-token check would even run.
            File f = file("report.pdf", "/1", false, FileStatus.TRASHED);
            given(findFilePort.findById(new FileId(f.getId()))).willReturn(Optional.of(f));

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(f.getId().toString(), key.toString())));
        }
    }

    @Nested
    @DisplayName("잘못된 입력")
    class WhenInputIsBad {

        @Test
        void unknownFileId() {
            // target() rejects an unresolvable fileId immediately, before any key is even parsed.
            UUID id = UUID.randomUUID();
            given(findFilePort.findById(new FileId(id))).willReturn(Optional.empty());

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(id.toString(), key.toString())));
        }

        @Test
        void malformedFileId() {
            // Rejected by target()'s own UUID parsing before any key is ever consulted.
            assertNotFound(catchThrowable(() -> publicFileResolver.resolve("not-a-uuid", key.toString())));
        }

        @Test
        void unknownKey() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED); // no enableLinkSharing
            givenFound(f);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.empty());
            given(findFileSharePort.findByToken(key)).willReturn(Optional.empty());

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(f.getId().toString(), key.toString())));
        }

        @Test
        void malformedKey() {
            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(UUID.randomUUID().toString(), "not-a-uuid")));
        }

        @Test
        void nullKey() {
            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(UUID.randomUUID().toString(), null)));
        }

        @Test
        void blankKey() {
            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(UUID.randomUUID().toString(), "  ")));
        }

        @Test
        @DisplayName("key가 다른 파일의 것이고 fileId는 그 범위 밖일 때")
        void keyUnlocksAnotherFile() {
            File a = file("a.pdf", "/1", false, FileStatus.UPLOADED);
            a.enableLinkSharing(key, Role.VIEWER);
            File b = file("b.pdf", "/1", false, FileStatus.UPLOADED);
            givenFound(b);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(a));

            assertNotFound(catchThrowable(() -> publicFileResolver.resolve(b.getId().toString(), key.toString())));
        }
    }

    @Nested
    @DisplayName("key가 LINK 공개 폴더의 토큰일 때")
    class WhenKeyIsALinkSharedFolder {

        private File folder() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED);
            folder.enableLinkSharing(key, Role.VIEWER);
            return folder;
        }

        @Test
        void listsTheFoldersOwnNonDeletedChildren() {
            File folder = folder();
            givenFound(folder);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            File trashed = file("b.txt", "/shared", false, FileStatus.TRASHED);
            given(findFilePort.findByNamespaceIdAndPath(any(), eq("/shared"))).willReturn(List.of(child, trashed));

            assertThat(publicFileResolver.resolveChildren(folder.getId().toString(), key.toString()))
                    .containsExactly(child);
        }

        @Test
        void resolvesADescendantUnderTheFolder() {
            File folder = folder();
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            File child = file("a.txt", "/shared", false, FileStatus.UPLOADED);
            givenFound(child);

            assertThat(publicFileResolver.resolve(child.getId().toString(), key.toString())).isEqualTo(child);
        }

        @Test
        void rejectsAnEntryThatIsNotUnderTheFolder() {
            File folder = folder();
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            File outsider = file("x.txt", "/other", false, FileStatus.UPLOADED);
            givenFound(outsider);

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(outsider.getId().toString(), key.toString())));
        }

        @Test
        @DisplayName("이름이 공유 폴더명으로 시작하는 형제 폴더의 파일은 거부 (prefix 충돌)")
        void rejectsASiblingWhosePathMerelySharesAPrefix() {
            File folder = folder();
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            File sibling = file("secret.txt", "/sharedX", false, FileStatus.UPLOADED);
            givenFound(sibling);

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(sibling.getId().toString(), key.toString())));
        }

        @Test
        @DisplayName("경로는 같지만 다른 namespace의 파일은 거부")
        void rejectsAnEntryWithTheSamePathInAnotherNamespace() {
            File folder = folder();
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            File otherNs = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("a.txt"), new FilePath("/shared"), new FileOwnerId(UUID.randomUUID()),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            givenFound(otherNs);

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolve(otherNs.getId().toString(), key.toString())));
        }

        @Test
        void rejectsWhenTheFolderIsNotLinkShared() {
            File folder = file("shared", "/", true, FileStatus.UPLOADED); // no enableLinkSharing
            givenFound(folder);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            given(findFileSharePort.findByToken(key)).willReturn(Optional.empty());

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolveChildren(folder.getId().toString(), key.toString())));
        }

        @Test
        void rejectsResolveChildrenOnANonDirectory() {
            File f = file("report.pdf", "/1", false, FileStatus.UPLOADED);
            f.enableLinkSharing(key, Role.VIEWER);
            givenFound(f);
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(f));

            assertNotFound(catchThrowable(
                    () -> publicFileResolver.resolveChildren(f.getId().toString(), key.toString())));
        }

        @Test
        void listsASubDirectory() {
            File folder = folder();
            given(findFilePort.findByLinkToken(key)).willReturn(Optional.of(folder));
            File sub = file("sub", "/shared", true, FileStatus.UPLOADED);
            givenFound(sub);
            File nested = file("c.txt", "/shared/sub", false, FileStatus.UPLOADED);
            given(findFilePort.findByNamespaceIdAndPath(any(), eq("/shared/sub"))).willReturn(List.of(nested));

            assertThat(publicFileResolver.resolveChildren(sub.getId().toString(), key.toString()))
                    .containsExactly(nested);
        }
    }
}
