package com.moduDrive.auth.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberAuthData {

    private final String memberId;
    private final List<String> memberRoles;

    public static MemberAuthData create(MemberId memberId,
                                        MemberRoles memberRoles) {
        return new MemberAuthData(
                memberId.idValue,
                memberRoles.roleValues
        );
    }

    @Value
    public static class MemberId {
        public MemberId(String value) {
            this.idValue = value;
        }
        String idValue;
    }

    @Value
    public static class MemberRoles {
        public MemberRoles(List<String> values) {
            this.roleValues = values;
        }
        List<String> roleValues;
    }

}
