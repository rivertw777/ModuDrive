package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;

import java.util.UUID;

public interface SaveFilePort {

    File saveFile(File file);

    /** Purge from trash: drop blocks/versions/shares/favorites, keep the metadata row as a
     * tombstone with {@code deletedAt} stamped. {@code deletedBy} is null for a system-triggered
     * purge (the retention sweep) — there's no caller to attribute it to. */
    void purgeFile(FileId fileId, UUID deletedBy);

    /** Hard delete — everything, row included. Not used by the trash flow (see {@link #purgeFile});
     * for a future tombstone-cleanup job. */
    void deleteFile(FileId fileId);
}
