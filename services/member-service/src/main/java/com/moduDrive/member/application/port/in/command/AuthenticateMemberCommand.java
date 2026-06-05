package com.moduDrive.member.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import com.moduDrive.member.domain.model.Member.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@EqualsAndHashCode(callSuper = false)
public class AuthenticateMemberCommand extends SelfValidating<AuthenticateMemberCommand> {

    @NotNull
    private final MemberEmail memberEmail;

    @NotNull
    private final MemberPassword memberPassword;

    public AuthenticateMemberCommand(MemberEmail memberEmail, MemberPassword memberPassword) {
        this.memberEmail = memberEmail;
        this.memberPassword = memberPassword;
        this.validateSelf();
    }
}
