package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.domain.vo.MemberPassword;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@EqualsAndHashCode(callSuper = false)
public class LoginCommand extends SelfValidating<LoginCommand> {

    @NotNull
    private final MemberEmail memberEmail;

    @NotNull
    private final MemberPassword memberPassword;

    public LoginCommand(MemberEmail memberEmail, MemberPassword memberPassword) {
        this.memberEmail = memberEmail;
        this.memberPassword = memberPassword;
        this.validateSelf();
    }
}