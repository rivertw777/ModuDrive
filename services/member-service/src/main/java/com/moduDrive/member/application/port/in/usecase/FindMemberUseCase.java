package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.FindMemberCommand;
import com.moduDrive.member.domain.model.Member;

public interface FindMemberUseCase {
    Member findMember(FindMemberCommand findMemberCommand);
}
