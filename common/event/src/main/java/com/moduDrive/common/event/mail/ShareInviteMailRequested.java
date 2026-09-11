package com.moduDrive.common.event.mail;

import java.util.UUID;

/** Published by file-service (topic {@link MailTopics#SHARE_INVITE_REQUESTED}) after a share invite commits.
 * {@code inviteToken} is only set when the invite went to an email with no ModuDrive member (a "guest"
 * invite) — it is that pending share's own capability token, and mail-service turns it into a no-login
 * link; a registered member gets null and signs in instead. */
public record ShareInviteMailRequested(UUID fileId, String granteeEmail, String fileName, String role, UUID inviteToken) {
}
