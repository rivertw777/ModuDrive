package com.moduDrive.member.adapter.in.web.dto;

public record FindMemberResponse(
        String id,
        String name,
        String email,
        boolean isValid
) {
}
