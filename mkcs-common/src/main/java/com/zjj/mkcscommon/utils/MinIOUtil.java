package com.zjj.mkcscommon.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.net.URI;

/**
 * MinIO 工具类
 */
@Data
@AllArgsConstructor
@Slf4j
public class MinIOUtil {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;

    private static final Region MINIO_REGION = Region.of("us-east-1");

    /**
     * 创建 S3Client 对象以连接 MinIO 服务器
     *
     * @return S3Client 实例
     */
    public S3Client createS3Client() {
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(MINIO_REGION)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    /**
     * 上传文件到 MinIO 服务器
     *
     * @param file       要上传的文件
     * @param objectName 在 MinIO 中存储的对象名称
     * @return 文件的访问 URL
     */
    public String upload(MultipartFile file, String objectName) {
        try {
            S3Client s3Client = createS3Client();
            // 1. 获取文件内容类型和大小
            String contentType = file.getContentType();
            long contentLength = file.getSize();

            // 2. 构建 PutObjectRequest
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(contentType)
                    // 建议设置内容长度
                    .contentLength(contentLength)
                    .build();

            // 3. 将 MultipartFile 的 InputStream 包装成 RequestBody
            // 使用 fromInputStream() 方法，并提供内容长度
            RequestBody requestBody = null;
            requestBody = RequestBody.fromInputStream(
                    file.getInputStream(),
                    contentLength
            );

            // 4. 执行上传
            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);
            log.info("文件上传到: {}/{}", bucketName, objectName);

        } catch (Exception e) {
            log.error("上传出错：{}", e.getMessage());
        }
        StringBuilder url = new StringBuilder(endpoint);
        url
            .append("/")
            .append(bucketName)
            //.append(".")
            //.append(endpoint)
            .append("/")
            .append(objectName);
        log.info("上传成功，文件访问路径: {}", url.toString());
        return url.toString();
    }
}
