package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.AuthenticateMemberCommand;
import com.moduDrive.member.domain.model.Member;

public interface AuthenticateMemberUseCase {
    Member authenticateMember(AuthenticateMemberCommand authenticateMemberCommand);
}
