package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair.*;

public interface ValidateTokenPort {
    MemberAuthData getMemberAuthDataFromToken(AccessToken accessToken);
}
