package com.zjj.mkcscommon.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinIOUtilStoragePathTest {

    private final MinIOUtil minIOUtil = new MinIOUtil(
            "http://127.0.0.1:19000", "http://127.0.0.1:19000", "access", "secret", "files");

    @Test
    void parsesHistoricalFullObjectUrl() {
        MinIOUtil.ObjectLocation location = minIOUtil.parseStoragePath(
                "http://127.0.0.1:19000/files/f79a5b1c-f391-42fc-8317-c053c4d074e6.png", "files");

        assertThat(location.bucketName()).isEqualTo("files");
        assertThat(location.objectKey()).isEqualTo("f79a5b1c-f391-42fc-8317-c053c4d074e6.png");
    }

    @Test
    void parsesCurrentBucketAndObjectKeyFormat() {
        MinIOUtil.ObjectLocation location = minIOUtil.parseStoragePath("files/contents/object-id", "files");

        assertThat(location.bucketName()).isEqualTo("files");
        assertThat(location.objectKey()).isEqualTo("contents/object-id");
    }

    @Test
    void rejectsUntrustedOrInvalidStoragePaths() {
        assertThatThrownBy(() -> minIOUtil.parseStoragePath("http://example.test/files/object", "files"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> minIOUtil.parseStoragePath("avatars/object", "files"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> minIOUtil.parseStoragePath("files/contents/../object", "files"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void presignsDownloadWithThePublicEndpoint() {
        MinIOUtil signingUtil = new MinIOUtil(
                "http://minio.internal:9000", "http://files.example:9000", "test-access", "test-secret", "files");
        signingUtil.initS3Client();
        try {
            String url = signingUtil.presignDownload("files", "contents/object-id", "report.pdf", Duration.ofSeconds(60));

            assertThat(url).startsWith("http://files.example:9000/files/contents/object-id?");
            assertThat(url).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256", "X-Amz-Signature=");
        } finally {
            signingUtil.destroyS3Client();
        }
    }
}
