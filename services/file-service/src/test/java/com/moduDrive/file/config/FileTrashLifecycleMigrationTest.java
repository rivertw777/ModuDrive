package com.moduDrive.file.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FileTrashLifecycleMigrationTest {

    private JdbcTemplate jdbcTemplate;
    private FileTrashLifecycleMigration migration;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        migration = new FileTrashLifecycleMigration(jdbcTemplate);
    }

    @Nested
    @DisplayName("레거시 스키마일 때")
    class WhenLegacySchema {

        @BeforeEach
        void seedLegacySchema() {
            // ddl-auto has already added trashed_at/deleted_at; the pre-trashed_at
            // retention-sweep index is still around.
            jdbcTemplate.execute("""
                    CREATE TABLE file (
                        id UUID PRIMARY KEY, status VARCHAR(20), updated_at TIMESTAMP,
                        trashed_at TIMESTAMP, deleted_at TIMESTAMP)
                    """);
            jdbcTemplate.execute("CREATE INDEX ix_file_status_updated_at ON file (status, updated_at)");
        }

        @Test
        @DisplayName("휴지통에 있는 파일의 trashed_at을 updated_at으로 채우고 구 인덱스를 드롭한다")
        void backfillsTrashedAtAndDropsStaleIndex() {
            UUID trashed = UUID.randomUUID();
            UUID active = UUID.randomUUID();
            LocalDateTime trashedOn = LocalDateTime.now().minusDays(2).withNano(0);
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at) VALUES (?, 'DELETED', ?, NULL)",
                    trashed, trashedOn);
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at) VALUES (?, 'UPLOADED', ?, NULL)",
                    active, LocalDateTime.now());

            migration.run(null);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trashed_at FROM file WHERE id = ?", LocalDateTime.class, trashed)).isEqualTo(trashedOn);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trashed_at FROM file WHERE id = ?", LocalDateTime.class, active)).isNull();
            assertThat(jdbcTemplate.queryForList(
                    "SELECT index_name FROM information_schema.indexes WHERE table_name = 'FILE'", String.class))
                    .doesNotContain("IX_FILE_STATUS_UPDATED_AT");
        }

        @Test
        @DisplayName("아직 퍼지되지 않은 DELETED 행은 TRASHED로, 이미 퍼지된 행은 그대로 DELETED로 남는다")
        void splitsTrashedFromPurgedDeleted() {
            UUID notYetPurged = UUID.randomUUID();
            UUID alreadyPurged = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at, deleted_at) "
                            + "VALUES (?, 'DELETED', ?, ?, NULL)",
                    notYetPurged, LocalDateTime.now(), LocalDateTime.now());
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at, deleted_at) "
                            + "VALUES (?, 'DELETED', ?, ?, ?)",
                    alreadyPurged, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());

            migration.run(null);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM file WHERE id = ?", String.class, notYetPurged)).isEqualTo("TRASHED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM file WHERE id = ?", String.class, alreadyPurged)).isEqualTo("DELETED");
        }

        @Test
        @DisplayName("다시 돌려도 오류 없이 끝난다")
        void isIdempotent() {
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at) VALUES (?, 'DELETED', ?, NULL)",
                    UUID.randomUUID(), LocalDateTime.now());

            migration.run(null);
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("trashed_at 백필이 실패할 때")
    class WhenTrashedAtBackfillFails {

        @BeforeEach
        void seedSchemaMissingTrashedAt() {
            // No trashed_at column at all — backfillTrashedAt's UPDATE throws, but status/deleted_at
            // are still there, so splitTrashedFromDeleted's UPDATE would otherwise succeed fine.
            jdbcTemplate.execute("""
                    CREATE TABLE file (
                        id UUID PRIMARY KEY, status VARCHAR(20), updated_at TIMESTAMP, deleted_at TIMESTAMP)
                    """);
        }

        @Test
        @DisplayName("status를 TRASHED로 넘기는 2단계는 건너뛴다")
        void skipsTheStatusSplitStep() {
            UUID trashed = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, deleted_at) VALUES (?, 'DELETED', ?, NULL)",
                    trashed, LocalDateTime.now());

            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();

            // Still DELETED — if step 2 had run despite step 1's failure, this would read TRASHED
            // with trashed_at forever unbackfillable (the exact bug the ordering guard prevents).
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM file WHERE id = ?", String.class, trashed)).isEqualTo("DELETED");
        }
    }

    @Nested
    @DisplayName("신규 DB 라 file 테이블이 아직 없을 때")
    class WhenFreshDatabase {

        @Test
        @DisplayName("오류를 삼키고 애플리케이션 기동을 막지 않는다")
        void neverPropagatesTheMissingTable() {
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }
}
