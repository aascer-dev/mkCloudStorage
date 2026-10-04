package cn.zjj.mkcsserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationResourceTest {

    @Test
    void initialSchemaMigrationExistsAndCannotDropTables() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V1__initial_schema.sql");

        assertThat(resource.exists()).isTrue();
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("CREATE TABLE");
        assertThat(sql).doesNotContain("DROP TABLE");
    }

    @Test
    void multipartMigrationIsVersionedForAutomaticExecution() {
        assertThat(new ClassPathResource("db/migration/V20260917_001__add_multipart_upload_task_columns.sql").exists())
                .isTrue();
    }

    @Test
    void storageUsageBackfillMigrationCountsActiveLogicalFiles() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V20260917_002__backfill_storage_bucket_usage.sql");

        assertThat(resource.exists()).isTrue();
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("SUM(size)", "status = 1", "is_folder = 0", "used_storage");
    }
}
