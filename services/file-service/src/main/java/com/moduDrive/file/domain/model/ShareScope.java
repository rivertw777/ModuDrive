package com.moduDrive.file.domain.model;

public enum ShareScope {

    /** Only the owner and explicitly invited members. */
    RESTRICTED,

    /** Anyone who has this entry's {@code fileId} — no separate token needed, since the id itself
     * is already an unguessable capability (issue #303). Always grants {@link Role#VIEWER}
     * ({@link File#enableLinkSharing()} makes any other role unrepresentable, #318): a link is a
     * bearer credential anyone who obtains it can use, so it can never identify who is editing. */
    LINK
}
