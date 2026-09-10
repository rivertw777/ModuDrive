package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView.InheritedShare;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListFileSharesController.class)
@Import(GlobalExceptionHandler.class)
class ListFileSharesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListFileSharesUseCase listFileSharesUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("소유자가 공유 목록을 조회할 때")
    class WhenOwnerLists {

        @Test
        void returnsOwnerAndShares() throws Exception {
            File file = File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1"),
                    new FileOwnerId(UUID.fromString(OWNER_ID)), null, null,
                    FileStatus.UPLOADED, new FileIsDirectory(false));
            UUID sharedWithUserId = UUID.randomUUID();
            FileShare share = FileShare.withId(new FileShareId(UUID.randomUUID()), new FileShareFileId(FILE_ID),
                    new FileShareOwnerId(UUID.fromString(OWNER_ID)),
                    new FileShareSharedWithUserId(sharedWithUserId), new FileShareRole(Role.EDITOR));
            given(listFileSharesUseCase.listFileShares(any(ListFileSharesCommand.class)))
                    .willReturn(new FileSharesView(file, List.of(share), List.of(), List.of(),
                            Map.of(sharedWithUserId, new MemberSummary("river", "river@modudrive.com")), false));

            mockMvc.perform(get("/api/v1/files/{fileId}/shares", FILE_ID)
                            .header("X_USER_ID", OWNER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ownerId").value(OWNER_ID))
                    .andExpect(jsonPath("$.data.scope").value("RESTRICTED"))
                    .andExpect(jsonPath("$.data.shares[0].role").value("EDITOR"))
                    .andExpect(jsonPath("$.data.shares[0].sharedWithEmail").value("river@modudrive.com"))
                    .andExpect(jsonPath("$.data.shares[0].sharedWithName").value("river"));
        }
    }

    @Nested
    @DisplayName("상위 폴더에 미가입 게스트 초대가 있을 때")
    class WhenAnAncestorHasAPendingGuestInvite {

        @Test
        @DisplayName("이메일과 함께 상속 항목으로 내려준다 (issue #313 — 예전엔 이 지점에서 NPE로 500)")
        void returnsInheritedPendingGuestInviteWithItsEmail() throws Exception {
            File file = File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/shared-folder"),
                    new FileOwnerId(UUID.fromString(OWNER_ID)), null, null,
                    FileStatus.UPLOADED, new FileIsDirectory(false));
            UUID parentId = UUID.randomUUID();
            File parentDir = File.withId(new FileId(parentId), new FileNamespaceId(file.getNamespaceId()),
                    new FileName("shared-folder"), new FilePath("/"),
                    new FileOwnerId(UUID.fromString(OWNER_ID)), null, null,
                    FileStatus.UPLOADED, new FileIsDirectory(true));
            FileShare pendingGuestInvite = FileShare.withId(new FileShareId(UUID.randomUUID()),
                    new FileShareFileId(parentId), new FileShareOwnerId(UUID.fromString(OWNER_ID)), null,
                    new FileShareRole(Role.VIEWER), UUID.randomUUID(), "guest@example.com", null);
            given(listFileSharesUseCase.listFileShares(any(ListFileSharesCommand.class)))
                    .willReturn(new FileSharesView(file, List.of(),
                            List.of(new InheritedShare(pendingGuestInvite, parentDir)),
                            List.of(), Map.of(), false));

            mockMvc.perform(get("/api/v1/files/{fileId}/shares", FILE_ID)
                            .header("X_USER_ID", OWNER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.shares[0].sharedWithEmail").value("guest@example.com"))
                    .andExpect(jsonPath("$.data.shares[0].sharedWithName").doesNotExist())
                    .andExpect(jsonPath("$.data.shares[0].inheritedFrom.fileId").value(parentId.toString()));
        }
    }

    @Nested
    @DisplayName("호출자가 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void returnsForbidden() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(listFileSharesUseCase).listFileShares(any(ListFileSharesCommand.class));

            mockMvc.perform(get("/api/v1/files/{fileId}/shares", FILE_ID)
                            .header("X_USER_ID", OWNER_ID))
                    .andExpect(status().isForbidden());
        }
    }
}
