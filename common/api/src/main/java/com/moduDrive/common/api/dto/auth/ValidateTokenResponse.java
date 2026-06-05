package com.moduDrive.common.api.dto.auth;

import java.util.List;

public record ValidateTokenResponse(
        String memberId,
        List<String> memberRoles
) {
}