package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListSharedDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.ListSharedDirectoryUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ListSharedDirectoryService implements ListSharedDirectoryUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final FileFavoritePort fileFavoritePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<FileView> listSharedDirectory(ListSharedDirectoryCommand command) {
        UUID callerId = command.getCallerId();
        File directory = findFilePort.findById(command.getDirectoryId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        // READ is enough to list contents; requirePermission already honours an inherited grant
        // from a directory further up, so a caller who was shared an ancestor can browse here too.
        fileAccessGuard.requirePermission(directory, callerId, Permission.READ);

        if (!directory.isDirectory()) {
            throw new BusinessException(FileExceptionCase.DIRECTORY_NOT_FOUND);
        }

        Set<UUID> favoriteIds = fileFavoritePort.favoriteFileIds(callerId);
        // Children of a shared folder inherit the caller's role *and* the "공유한 사용자"/"공유된
        // 날짜" attribution from that folder's own grant — 공유 문서함 shows the same columns whether
        // you're at the root or three folders deep, so resolve both once here rather than per row.
        // inheritableRole, not effectiveRole: from a child's point of view `directory` is just its
        // nearest ancestor, so its own grant wins outright over a grandparent's — same rule as a
        // file's own direct grant in effectiveRole. Kept as a separate method purely to name this
        // call site's intent ("what does a child inherit"), not because the logic differs.
        Role inheritedRole = fileAccessGuard.inheritableRole(directory, callerId);
        Optional<FileShare> grant = fileAccessGuard.resolveGrant(directory, callerId);
        MemberSummary sharedBy = findMemberByIdPort.findMemberByIdOrUnknown(directory.getOwnerId());
        LocalDateTime sharedAt = grant.map(FileShare::getCreatedAt).orElse(null);

        return findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(directory.getNamespaceId()), directory.fullPath())
                .stream()
                .filter(child -> !child.isRemoved())
                .map(child -> {
                    child.markFavorite(favoriteIds.contains(child.getId()));
                    if (child.getOwnerId().equals(callerId)) {
                        return FileView.owned(child);
                    }
                    // A child can also hold its own direct share to the caller (see
                    // ShareFileService) alongside this folder's inherited one — unfiltered, so it
                    // shows here too. The direct grant, if any, wins outright (matches
                    // FileAccessGuard.resolveRole — a grant on the exact file is a deliberate,
                    // file-specific decision by the owner and is never overridden by an inherited
                    // one, more generous or not); the date is the direct grant's own, since that's
                    // the row the caller was actually notified about.
                    // ponytail: one query per child (N+1) — add a batch
                    // findByFileIdInAndSharedWithUserId(List<FileId>, UUID) lookup if a folder with
                    // hundreds of entries makes this measurably slow.
                    Optional<FileShare> direct =
                            findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(child.getId()), callerId);
                    Role role = direct.map(FileShare::getRole).orElse(inheritedRole);
                    LocalDateTime sharedOn = direct.map(FileShare::getCreatedAt).orElse(sharedAt);
                    return new FileView(child, role, sharedBy.name(), sharedBy.email(), sharedOn, null, null);
                })
                .toList();
    }
}
