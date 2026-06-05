package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.member.application.port.in.command.FindMemberCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberUseCase;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.domain.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class FindMemberService implements FindMemberUseCase{

    private final FindMemberPort findMemberPort;

    @Transactional(readOnly = true)
    public Member findMember(FindMemberCommand findMemberCommand) {
        return findMemberPort.findMemberById(
                findMemberCommand.getMemberId()
        );
    }

}
