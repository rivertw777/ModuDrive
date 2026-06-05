package com.moduDrive.member.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import com.moduDrive.member.domain.model.Member.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@EqualsAndHashCode(callSuper = false)
public class FindMemberCommand extends SelfValidating<FindMemberCommand> {

    @NotNull
    private final MemberId memberId;

    public FindMemberCommand(MemberId memberId) {
        this.memberId = memberId;
        this.validateSelf();
    }
}
