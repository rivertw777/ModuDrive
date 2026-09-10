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
        UUID linkToken,
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
    /** {@code linkToken} is the ancestor's own capability, not this file's — the client builds
     * this file's public link as {@code /public/{thisFileId}?key={linkToken}} (see
     * PublicFileResolver.unlocks: any entry nested under the token's root resolves), since this
     * file has no linkToken of its own while merely inheriting LINK access. */
    public record InheritedLinkResponse(UUID fileId, String name, Role role, UUID linkToken) {}

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
            var summary = view.memberSummaries().get(inherited.share().getSharedWithUserId());
            shares.add(FileShareResponse.inherited(
                    inherited.share(), summary.email(), summary.name(),
                    inherited.source().getId(), inherited.source().getName()));
        }

        List<InheritedLinkResponse> inheritedLinks = view.inheritedLinkSources().stream()
                .map(source -> new InheritedLinkResponse(
                        source.getId(), source.getName(), source.getLinkRole(), source.getLinkToken()))
                .toList();

        return new FileAccessListResponse(
                view.file().getId(),
                view.file().getOwnerId(),
                view.file().getAccessScope(),
                view.file().getLinkToken(),
                shares,
                inheritedLinks,
                view.hasSharedDescendant()
        );
    }
}
