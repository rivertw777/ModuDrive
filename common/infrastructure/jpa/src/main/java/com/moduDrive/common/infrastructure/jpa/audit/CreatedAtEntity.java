package com.moduDrive.common.infrastructure.jpa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** For an append-only/log-style table that only ever needs "who created this row and when" —
 * no update/soft-delete lifecycle, so no {@link BaseTimeEntity}. Same ddl-auto=update
 * nullability rule as everywhere else in this project: left DB-nullable so adding the column to
 * a populated table doesn't break the migration. */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class CreatedAtEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Auto-stamped from the caller's {@code X_USER_ID} header — see AuditingConfig#auditorAware. */
    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;
}
