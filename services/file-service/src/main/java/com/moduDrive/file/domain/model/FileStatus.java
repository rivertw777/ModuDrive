package com.moduDrive.file.domain.model;

import java.util.Set;

public enum FileStatus {
    /** Upload started, not yet completed — no committed content/size. */
    PENDING,
    /** Live, in the file tree. */
    UPLOADED,
    /** In the trash — soft-deleted, still recoverable via restore. {@code File.trashedAt} is set. */
    TRASHED,
    /** Purged from the trash — a tombstone row. Blocks/versions/shares/favorites are gone;
     * {@code File.deletedAt} is set and never un-sets. Not reachable from TRASHED except through
     * a purge. */
    DELETED;

    /** TRASHED or DELETED — anything no longer part of the live file tree. Used wherever a query
     * or check needs "show me only what's actually there" (excluding both) in one place, so the
     * two statuses stay in sync as a pair rather than duplicated at every call site. */
    public static final Set<FileStatus> REMOVED = Set.of(TRASHED, DELETED);
}
