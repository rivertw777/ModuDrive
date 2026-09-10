package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** {@code fileId} kept as a raw String for the same reason as {@link GetPublicFileCommand}: an
 * unauthenticated caller supplies it, so a malformed value must 404 like a wrong one. The
 * directory to list — the link's own folder or any folder nested under it. */
@Getter
public class ListPublicDirectoryCommand {

    private final String fileId;

    public ListPublicDirectoryCommand(String fileId) {
        this.fileId = fileId;
    }
}
