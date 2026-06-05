package com.moduDrive.member.application.port.out;

import com.moduDrive.member.domain.model.Member.*;

public interface MatchesPasswordPort {
    boolean matchesPassword(MemberPassword rawPassword, MemberPassword encodedPassword);
}
