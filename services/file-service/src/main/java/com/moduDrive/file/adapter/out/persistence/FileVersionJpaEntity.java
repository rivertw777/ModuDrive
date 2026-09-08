package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "file_version")
@Entity
class FileVersionJpaEntity extends CreatedAtEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private UUID fileId;

    private Long fileSize;

    private int blockCount;

    @Column(nullable = false)
    private String s3Path;

    FileVersionJpaEntity(UUID fileId, Long fileSize, int blockCount, String s3Path) {
        this.fileId = fileId;
        this.fileSize = fileSize;
        this.blockCount = blockCount;
        this.s3Path = s3Path;
    }
}
