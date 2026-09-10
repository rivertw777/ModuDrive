package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** {@code fileId} kept as a raw String for the same reason as {@link GetPublicFileCommand}: an
 * unauthenticated caller supplies it, so a malformed value must 404 like a wrong one. The
 * directory to list — the link's own folder or any folder nested under it. {@code key} is the
 * same optional guest-invite token {@link GetPublicFileCommand} carries: a folder invite reaches
 * everything nested under the invited folder, so listing needs it too, not just LINK scope. */
@Getter
public class ListPublicDirectoryCommand {

    private final String fileId;
    private final String key;

    public ListPublicDirectoryCommand(String fileId, String key) {
        this.fileId = fileId;
        this.key = key;
    }
}
