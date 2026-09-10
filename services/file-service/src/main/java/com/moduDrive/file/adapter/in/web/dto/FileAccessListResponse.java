package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FileAccessListResponse(
        UUID fileId,
        UUID ownerId,
        ShareScope scope,
        /** Direct rows first, then inherited rows — inherited rows are root-most ancestor first
         * (see {@code ListFileSharesService}/{@code FileAccessGuard.ancestorDirectories}). The web
         * layer relies on this order: when the same grantee has no direct row but is listed via
         * two independent ancestors, it picks the *last* matching inherited row as "nearest
         * ancestor" (see {@code MemberAccessList}), which is now the actual effective role, not
         * just a display tie-break — don't reorder these without updating that. */
        List<FileShareResponse> shares,
        /** Directories above this file that are currently "anyone with the link" — this file is
         * reachable through them. Empty unless an ancestor folder is link-shared. */
        List<InheritedLinkResponse> inheritedLinks,
        /** See {@link FileSharesView#hasSharedDescendant()}. */
        boolean hasSharedDescendant
) {
    /** A directory above this file that is currently link-shared — this file's own scope stays
     * whatever it is, but it's reachable through this ancestor's LINK scope regardless (see
     * {@code FileAccessGuard.linkRole} / {@code PublicFileResolver}, issue #303). */
    public record InheritedLinkResponse(UUID fileId, String name, Role role) {}

    public static FileAccessListResponse from(FileSharesView view) {
        List<FileShareResponse> shares = new ArrayList<>();

        for (var share : view.shares()) {
            // A pending guest share has no member to look up — its own granteeEmail is the
            // display email, and it has no member display name.
            if (share.getSharedWithUserId() == null) {
                shares.add(FileShareResponse.from(share, share.getGranteeEmail(), null));
            } else {
                var summary = view.memberSummaries().get(share.getSharedWithUserId());
                shares.add(FileShareResponse.from(share, summary.email(), summary.name()));
            }
        }

        for (var inherited : view.inheritedShares()) {
            var share = inherited.share();
            // Same pending-guest branch as the direct loop above (issue #313): an ancestor's
            // unclaimed invite has no member to look up either, and was previously dropped before
            // ever reaching this method — silently hiding it from the owner's share list even
            // though the guest could still get in through it.
            if (share.getSharedWithUserId() == null) {
                shares.add(FileShareResponse.inherited(share, share.getGranteeEmail(), null,
                        inherited.source().getId(), inherited.source().getName()));
            } else {
                var summary = view.memberSummaries().get(share.getSharedWithUserId());
                shares.add(FileShareResponse.inherited(share, summary.email(), summary.name(),
                        inherited.source().getId(), inherited.source().getName()));
            }
        }

        List<InheritedLinkResponse> inheritedLinks = view.inheritedLinkSources().stream()
                .map(source -> new InheritedLinkResponse(source.getId(), source.getName(), source.getLinkRole()))
                .toList();

        return new FileAccessListResponse(
                view.file().getId(),
                view.file().getOwnerId(),
                view.file().getAccessScope(),
                shares,
                inheritedLinks,
                view.hasSharedDescendant()
        );
    }
}
