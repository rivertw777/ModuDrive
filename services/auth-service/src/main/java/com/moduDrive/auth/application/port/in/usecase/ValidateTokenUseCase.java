package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.domain.model.MemberAuthData;

public interface ValidateTokenUseCase {
    MemberAuthData validateToken(ValidateTokenCommand validateTokenCommand);
}
