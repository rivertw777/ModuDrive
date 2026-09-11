package com.moduDrive.mail.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode
public class SendShareInviteMailCommand {

    private final String email;
    private final String fileName;
    private final String role;
    private final UUID fileId;
    /** Non-null only for a guest invite (no ModuDrive member owns the email) — this one invite's
     * own capability token, which the mail hands over as {@code /files/{fileId}?key=} so the
     * recipient can open the file without logging in. */
    private final UUID inviteToken;

    public SendShareInviteMailCommand(String email, String fileName, String role, UUID fileId, UUID inviteToken) {
        this.email = email;
        this.fileName = fileName;
        this.role = role;
        this.fileId = fileId;
        this.inviteToken = inviteToken;
    }
}
