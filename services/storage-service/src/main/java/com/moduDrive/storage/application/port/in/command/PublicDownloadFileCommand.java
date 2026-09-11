package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Kept as raw path/query Strings, not UUID: an unauthenticated caller supplies them, and a
 * malformed {@code fileId}/{@code key} must 404 out of file-service like a wrong one rather than
 * 500 here. {@code fileId} may be the directly-shared file or one nested under a shared folder,
 * and by itself already authorizes a LINK-scoped entry; {@code key} is the separate capability
 * that authorizes a guest invite instead. */
@Getter
@EqualsAndHashCode(callSuper = false)
public class PublicDownloadFileCommand extends SelfValidating<PublicDownloadFileCommand> {

    @NotBlank
    private final String fileId;

    /** Not @NotBlank on purpose: a missing key is the normal case for a LINK-scoped entry — see
     * {@code quotaScope} in {@code PublicDownloadFileService}, which treats it exactly that way.
     * A malformed or wrong key, like a missing one on a non-LINK entry, is rejected by
     * file-service (which this always calls first, via getPublicVersion) as a uniform
     * FILE_NOT_FOUND — validating it here would instead surface a distinguishable 400/500. */
    private final String key;

    /** True only for the inline-preview caller ({@code viewPublicFile}) — see
     * {@link DownloadFileCommand#isInlinePreview()}. */
    private final boolean inlinePreview;

    public PublicDownloadFileCommand(String fileId, String key) {
        this(fileId, key, false);
    }

    public PublicDownloadFileCommand(String fileId, String key, boolean inlinePreview) {
        this.fileId = fileId;
        this.key = key;
        this.inlinePreview = inlinePreview;
        this.validateSelf();
    }
}
