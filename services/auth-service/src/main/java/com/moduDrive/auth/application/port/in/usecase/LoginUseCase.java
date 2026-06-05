package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.domain.model.TokenPair;

public interface LoginUseCase {
    TokenPair login(LoginCommand loginCommand);
}
