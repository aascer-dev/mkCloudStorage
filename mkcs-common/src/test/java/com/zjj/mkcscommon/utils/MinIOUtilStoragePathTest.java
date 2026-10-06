package com.zjj.mkcscommon.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class MinIOUtilStoragePathTest {

    private final MinIOUtil minIOUtil = new MinIOUtil(
            "http://127.0.0.1:19000", "http://127.0.0.1:19000", "access", "secret", "files");

    @Test
    void uploadReturnsPublicUrl() {
        MinIOUtil util = new MinIOUtil(
                "http://minio:9000", "https://files.example.com/", "access", "secret", "files");
        S3Client client = mock(S3Client.class);
        ReflectionTestUtils.setField(util, "s3Client", client);
        when(client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build());

        assertThat(util.upload(new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1}),
                "avatar", "avatar.png")).isEqualTo("https://files.example.com/avatar/avatar.png");
    }

    @Test
    void normalizesInternalAndPublicAvatarUrlsAndKeepsThirdPartyUrls() {
        MinIOUtil util = new MinIOUtil(
                "http://minio:9000", "https://files.example.com/", "access", "secret", "files");
        String path = "/avatar/a%20b.jpg?avatarVersion=1";

        assertThat(util.normalizeBucketUrl("http://minio:9000" + path, "avatar"))
                .isEqualTo("https://files.example.com" + path);
        assertThat(util.normalizeBucketUrl("http://files.example.com" + path, "avatar"))
                .isEqualTo("https://files.example.com" + path);
        assertThat(util.normalizeBucketUrl("https://third-party.test" + path, "avatar"))
                .isEqualTo("https://third-party.test" + path);
        assertThat(util.normalizeBucketUrl("http://minio:9000/files/object", "avatar"))
                .isEqualTo("http://minio:9000/files/object");
        assertThat(util.parseStoragePath("https://files.example.com/avatar/avatar.jpg", "avatar").objectKey())
                .isEqualTo("avatar.jpg");
    }

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
