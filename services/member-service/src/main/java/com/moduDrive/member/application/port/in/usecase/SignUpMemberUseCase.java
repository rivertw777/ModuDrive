package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.SignUpMemberCommand;

public interface SignUpMemberUseCase {
    void signUpMember(SignUpMemberCommand signUpMemberCommand);
}
