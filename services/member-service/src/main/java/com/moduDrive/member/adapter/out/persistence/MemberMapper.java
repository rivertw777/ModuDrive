package com.moduDrive.member.adapter.out.persistence;

import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.*;
import org.springframework.stereotype.Component;

@Component
class MemberMapper {
    Member mapToDomainEntity(MemberJpaEntity memberJpaEntity) {
        return Member.withId(
                new MemberId(memberJpaEntity.getId()),
                new MemberName(memberJpaEntity.getName()),
                new MemberEmail(memberJpaEntity.getEmail()),
                new MemberPassword(memberJpaEntity.getPassword()),
                new MemberRoles(memberJpaEntity.getRoles()),
                new MemberIsValid(memberJpaEntity.isValid())
        );
    }
}