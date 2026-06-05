package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.model.TokenPair.*;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ValidateTokenCommand extends SelfValidating<LoginCommand> {

    @NotNull
    private final AccessToken accessToken;

    public ValidateTokenCommand(AccessToken accessToken) {
        this.accessToken = accessToken;
        this.validateSelf();
    }
}