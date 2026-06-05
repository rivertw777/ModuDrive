package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;

public interface AuthenticateMemberPort {
    MemberAuthData authenticateMember(AuthenticateMemberRequest authenticateMemberRequest);
}
