package com.moduDrive.common.api.dto.member;

import java.util.List;

public record AuthenticateMemberResponse(
        String id,
        String name,
        String email,
        boolean isValid,
        List<String> roles
) {
}
