package com.moduDrive.common.api.dto.member;

import jakarta.validation.constraints.NotBlank;

public record AuthenticateMemberRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
