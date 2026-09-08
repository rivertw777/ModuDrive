package com.moduDrive.common.infrastructure.jpa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseTimeEntity {

    @CreatedDate
    @Column
    private LocalDateTime createdAt;

    /** Auto-stamped from the caller's {@code X_USER_ID} header — see AuditingConfig#auditorAware.
     * Left DB-nullable like every other audit column here: ddl-auto=update can't add a NOT NULL
     * column to a table that already has rows, and a background job/Kafka consumer write has no
     * HTTP request to read the caller from. */
    @CreatedBy
    @Column(updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column
    private UUID updatedBy;

    @Column
    private LocalDateTime deletedAt;

    /** No {@code @DeletedBy} auditing hook exists in Spring Data — the caller passes their own id
     * in explicitly, same as they already do for {@code deletedAt} via this method. */
    @Column
    private UUID deletedBy;

    @Column
    private Boolean isDeleted = false;

    public void delete(UUID deletedBy) {
        isDeleted = true;
        deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }

}
