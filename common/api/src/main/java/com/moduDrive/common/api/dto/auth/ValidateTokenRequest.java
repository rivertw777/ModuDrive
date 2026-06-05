package com.moduDrive.common.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ValidateTokenRequest(
        @NotBlank String token
) {
}
