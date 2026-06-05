package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.member.adapter.in.web.dto.SignUpMemberRequest;
import com.moduDrive.member.application.port.in.command.SignUpMemberCommand;
import com.moduDrive.member.application.port.in.usecase.SignUpMemberUseCase;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberName;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
@RequiredArgsConstructor
class SignUpMemberController {

    private final SignUpMemberUseCase signUpMemberUseCase;

    @PostMapping("/api/v1/member/sign-up")
    public ApiResponse<Void> signUpMember(@Valid @RequestBody SignUpMemberRequest request) {
        val command = new SignUpMemberCommand(
                new MemberName(request.name()),
                new MemberEmail(request.email()),
                new MemberPassword(request.password())
        );
        signUpMemberUseCase.signUpMember(command);

        return ApiResponse.success();
    }

}
