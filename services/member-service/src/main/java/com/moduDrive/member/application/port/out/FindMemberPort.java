package com.moduDrive.member.application.port.out;

import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.*;

public interface FindMemberPort {
    Member findMemberByEmail(MemberEmail memberEmail);

    Member findMemberById(MemberId memberId);
}
