package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

/** The anonymous sibling of {@link DownloadFileService}: identical block assembly, but the file
 * is resolved by {@code (fileId, key)} instead of by id + caller, and file-service is the one
 * that decides whether that key still grants access. */
@UseCase
@RequiredArgsConstructor
class PublicDownloadFileService implements PublicDownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final DownloadQuotaPort downloadQuotaPort;
    private final StorageProperties storageProperties;

    @Override
    public byte[] downloadPublic(PublicDownloadFileCommand command) {
        GetFileVersionPort.VersionLocation version = locate(command);
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(version.blockCount(), storageProperties.getBlockSize());
        }
        String scope = quotaScope(command);
        // Anonymous fetches meter per link key: every recipient of one shared link draws on the
        // same window, but a stranger's traffic can't spend the owner's own (user-scoped) quota.
        downloadQuotaPort.checkWithinQuota(scope, version.s3Path());
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(version.s3Path(), version.blockCount());
        byte[] assembled = BlockAssembler.assemble(blocks);
        downloadQuotaPort.recordUsage(scope, version.s3Path(), assembled.length);
        return assembled;
    }

    @Override
    public void downloadPublicStream(PublicDownloadFileCommand command, OutputStream out) {
        GetFileVersionPort.VersionLocation version = locate(command);
        String scope = quotaScope(command);
        downloadQuotaPort.checkWithinQuota(scope, version.s3Path());
        CountingOutputStream counting = new CountingOutputStream(out);
        try {
            retrieveBlocksPort.streamBlocks(version.s3Path(), version.blockCount(), counting);
        } finally {
            downloadQuotaPort.recordUsage(scope, version.s3Path(), counting.count());
        }
    }

    private GetFileVersionPort.VersionLocation locate(PublicDownloadFileCommand command) {
        return getFileVersionPort.getPublicVersion(command.getFileId(), command.getKey());
    }

    /** Canonical form of whichever identity this anonymous fetch's quota draws on — re-casing or
     * dropping leading zeros (both of which {@code UUID.fromString} accepts and file-service
     * authorizes identically) can't mint a fresh bucket. {@code locate()} has already
     * round-tripped both {@code fileId} and a present {@code key} through file-service, so
     * whichever one this uses is a well-formed UUID by the time this runs.
     * <p>
     * A guest invite token is one person's own capability, so it's the natural meter. A keyless
     * request has no such per-person handle at all — LINK scope has been judged by {@code fileId}
     * alone since the link-token was retired (issue #303) — so it falls back to the file itself,
     * meaning every anonymous visitor of one link-shared file shares the same window (issue #312:
     * this fallback used to be missing entirely, NPEing on the {@code null} key instead). The
     * {@code link:}/{@code invite:} prefixes just keep the two scope spaces visibly distinct in
     * Redis; {@code s3Path} is already folded into the cache key alongside this, so a raw UUID
     * collision between them would be harmless anyway. */
    private static String quotaScope(PublicDownloadFileCommand command) {
        String key = command.getKey();
        return (key == null || key.isBlank())
                ? "link:" + UUID.fromString(command.getFileId())
                : "invite:" + UUID.fromString(key);
    }
}
