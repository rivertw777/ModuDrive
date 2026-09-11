package com.moduDrive.file.application.port.out;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public interface FindMemberByIdPort {

    Logger LOG = LoggerFactory.getLogger(FindMemberByIdPort.class);

    /** Resolves a member id to display info for share-list enrichment (see #156). */
    MemberSummary findMemberById(UUID memberId);

    /** Best-effort: a member-service hiccup (or the member simply no longer existing) degrades to
     * "unknown" display info instead of failing whatever listing needed it — enrichment is
     * display data, not the file/share data itself, so a lookup failure must never take down a
     * share list the owner needs to see to revoke access. Was five copies of this same
     * try/catch across the call sites below, each with its own local {@code UNKNOWN_MEMBER}
     * constant and a different exception type in the {@code catch}. */
    default MemberSummary findMemberByIdOrUnknown(UUID memberId) {
        try {
            return findMemberById(memberId);
        } catch (RuntimeException e) {
            LOG.warn("Failed to resolve member {}, showing as unknown", memberId, e);
            return UNKNOWN_MEMBER;
        }
    }

    MemberSummary UNKNOWN_MEMBER = new MemberSummary(null, null);

    record MemberSummary(String name, String email) {}
}
