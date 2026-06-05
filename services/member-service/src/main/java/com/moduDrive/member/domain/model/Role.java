package com.moduDrive.member.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Role {

    MEMBER("member"),
    ADMIN("admin");

    private final String value;
}
