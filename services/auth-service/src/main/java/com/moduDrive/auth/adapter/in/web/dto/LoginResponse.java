package com.moduDrive.auth.adapter.in.web.dto;

import java.util.Date;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String grantType,
        Date issuedAt
) {
}
