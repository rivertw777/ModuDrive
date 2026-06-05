package com.moduDrive.member.application.port.out;

import com.moduDrive.member.domain.model.Member.*;

public interface EncodePasswordPort {
    MemberPassword encodePassword(MemberPassword rawPassword);
}
