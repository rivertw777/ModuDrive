package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.GetPublicFileRevisionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileRevisionsUseCase;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** No caller id and no direct {@code FileAccessGuard} check — {@code fileId}/{@code key} are the
 * whole credential between them, and {@link PublicFileResolver} deciding one of them still
 * authorizes this entry is the authorization. Download is the only thing this enables, and every
 * link role includes it. */
@UseCase
@RequiredArgsConstructor
class GetPublicFileRevisionsService implements GetPublicFileRevisionsUseCase {

    private final PublicFileResolver publicFileResolver;
    private final FindFileVersionsPort findFileVersionsPort;

    @Transactional(readOnly = true)
    @Override
    public List<FileVersion> getPublicFileRevisions(GetPublicFileRevisionsCommand command) {
        File file = publicFileResolver.resolve(command.getFileId(), command.getKey());
        return findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(new FileId(file.getId()), command.getLimit());
    }
}
