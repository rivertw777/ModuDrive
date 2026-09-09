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

    private static final MemberSummary UNKNOWN_MEMBER = new MemberSummary(null, null);

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
        // inheritableRole, not effectiveRole: from a child's point of view `directory` is just one
        // more ancestor, so a grant on it must still fold with (not short-circuit over) whatever a
        // grandparent above it grants — the "direct grant wins outright" rule only applies at the
        // level of the file actually being accessed, i.e. each child below, not this directory.
        Role inheritedRole = fileAccessGuard.inheritableRole(directory, callerId);
        Optional<FileShare> grant = fileAccessGuard.resolveGrant(directory, callerId);
        MemberSummary sharedBy = lookupMember(directory.getOwnerId());
        LocalDateTime sharedAt = grant.map(FileShare::getCreatedAt).orElse(null);

        return findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(directory.getNamespaceId()), directory.fullPath())
                .stream()
                .filter(child -> !child.isRemoved())
                .map(child -> {
                    if (child.getOwnerId().equals(callerId)) {
                        return FileView.owned(child);
                    }
                    child.markFavorite(favoriteIds.contains(child.getId()));
                    // A child can also hold its own direct share to the caller (see
                    // ShareFileService) alongside this folder's inherited one — unfiltered, so it
                    // shows here too. The direct grant, if any, wins outright (matches
                    // FileAccessGuard.resolveRole — a grant on the exact file is a deliberate,
                    // file-specific decision by the owner and is never overridden by an inherited
                    // one, more generous or not); the date is the direct grant's own, since that's
                    // the row the caller was actually notified about.
                    Optional<FileShare> direct =
                            findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(child.getId()), callerId);
                    Role role = direct.map(FileShare::getRole).orElse(inheritedRole);
                    LocalDateTime sharedOn = direct.map(FileShare::getCreatedAt).orElse(sharedAt);
                    return new FileView(child, role, sharedBy.name(), sharedBy.email(), sharedOn, null, null);
                })
                .toList();
    }

    /** Best-effort: a member-service hiccup degrades to "shared by unknown", never fails the
     * whole listing the user needs to browse. */
    private MemberSummary lookupMember(UUID memberId) {
        try {
            return findMemberByIdPort.findMemberById(memberId);
        } catch (Exception e) {
            return UNKNOWN_MEMBER;
        }
    }
}
