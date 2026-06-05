package com.moduDrive.member.application.port.out;

import com.moduDrive.member.domain.model.Member.*;

public interface CheckEmailExistsPort {
    boolean existsByEmail(MemberEmail memberEmail);
}
