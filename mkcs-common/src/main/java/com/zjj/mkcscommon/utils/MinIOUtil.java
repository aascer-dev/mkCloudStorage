package com.zjj.mkcscommon.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MinIO 工具类
 */
@Slf4j
@Getter
public class MinIOUtil {
    private final String endpoint;
    private final String publicEndpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;

    private static final Region MINIO_REGION = Region.of("us-east-1");
    
    /**
     * 缓存的 S3Client 实例
     */
    private S3Client s3Client;
    private S3Presigner s3Presigner;

    /**
     * Internal object location used for S3 API calls. Object URLs must never be
     * passed to the SDK as an object key.
     */
    public record ObjectLocation(String bucketName, String objectKey) {
    }

    public MinIOUtil(String endpoint, String accessKey, String secretKey, String bucketName) {
        this(endpoint, endpoint, accessKey, secretKey, bucketName);
    }

    public MinIOUtil(String endpoint, String publicEndpoint, String accessKey, String secretKey, String bucketName) {
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
    }

    /**
     * 初始化 S3Client，在对象创建后执行一次
     */
    @PostConstruct
    public void initS3Client() {
        log.info("初始化 MinIO S3Client，endpoint: {}", endpoint);
        
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(MINIO_REGION)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey))
                .serviceConfiguration(s3Configuration)
                .build();

        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(URI.create(publicEndpoint))
                .region(MINIO_REGION)
                .credentialsProvider(() -> software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(accessKey, secretKey))
                .serviceConfiguration(s3Configuration)
                .build();
                
        log.info("MinIO S3Client 初始化完成");
    }

    /**
     * 销毁 S3Client，释放资源
     */
    @PreDestroy
    public void destroyS3Client() {
        if (s3Client != null) {
            log.info("关闭 MinIO S3Client");
            s3Client.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
    }

    /**
     * 获取缓存的 S3Client 实例
     *
     * @return S3Client 实例
     */
    public S3Client getS3Client() {
        if (s3Client == null) {
            throw new IllegalStateException("S3Client 未初始化，请确保对象已正确创建并调用了 @PostConstruct 方法");
        }
        return s3Client;
    }

    public String createMultipartUpload(String bucketName, String objectKey, String contentType) {
        try {
            String uploadId = getS3Client().createMultipartUpload(CreateMultipartUploadRequest.builder()
                    .bucket(bucketName).key(objectKey).contentType(contentType).build()).uploadId();
            if (uploadId == null || uploadId.isBlank()) {
                throw new IllegalStateException("MinIO未返回Multipart上传ID");
            }
            return uploadId;
        } catch (Exception e) {
            throw new RuntimeException("初始化MinIO分片上传失败", e);
        }
    }

    public String presignUploadPart(String bucketName, String objectKey, String uploadId, int partNumber, Duration signatureDuration) {
        if (s3Presigner == null) {
            throw new IllegalStateException("S3Presigner未初始化");
        }
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName).key(objectKey).uploadId(uploadId).partNumber(partNumber).build();
        PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(signatureDuration).uploadPartRequest(uploadPartRequest).build());
        return presigned.url().toString();
    }

    /** Generates a browser-accessible, short-lived URL for downloading one object. */
    public String presignDownload(String bucketName, String objectKey, String downloadFilename, Duration signatureDuration) {
        if (s3Presigner == null) {
            throw new IllegalStateException("S3Presigner未初始化");
        }
        String contentDisposition = "attachment; filename*=UTF-8''" + encodeContentDispositionFilename(downloadFilename);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .responseContentDisposition(contentDisposition)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .getObjectRequest(getObjectRequest)
                .build());
        return presigned.url().toString();
    }

    private String encodeContentDispositionFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "download";
        }
        return java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    public List<Part> listMultipartUploadParts(String bucketName, String objectKey, String uploadId) {
        List<Part> parts = new ArrayList<>();
        Integer marker = null;
        do {
            ListPartsResponse response = getS3Client().listParts(ListPartsRequest.builder()
                    .bucket(bucketName).key(objectKey).uploadId(uploadId).partNumberMarker(marker).maxParts(1000).build());
            parts.addAll(response.parts());
            marker = response.isTruncated() ? response.nextPartNumberMarker() : null;
        } while (marker != null);
        return parts;
    }

    public void completeMultipartUpload(String bucketName, String objectKey, String uploadId, List<CompletedPart> parts) {
        getS3Client().completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucketName).key(objectKey).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
    }

    public void abortMultipartUpload(String bucketName, String objectKey, String uploadId) {
        getS3Client().abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucketName).key(objectKey).uploadId(uploadId).build());
    }

    public long getObjectSize(String bucketName, String objectKey) {
        return getS3Client().headObject(HeadObjectRequest.builder().bucket(bucketName).key(objectKey).build()).contentLength();
    }

    /**
     * 将同一 MinIO 主机上指定存储桶的历史访问地址改为当前 endpoint。
     *
     * <p>对象 URL 曾被完整持久化，修改 MinIO 端口不会自动更新旧记录。该方法只处理
     * 同主机且路径属于指定桶的 URL，避免改写第三方头像地址。</p>
     *
     * @param objectUrl 已持久化的对象访问地址
     * @param bucketName 对象所在的存储桶
     * @return 使用当前 endpoint 的对象地址；不符合条件时返回原地址
     */
    public String normalizeBucketUrl(String objectUrl, String bucketName) {
        if (objectUrl == null || objectUrl.isBlank() || bucketName == null || bucketName.isBlank()) {
            return objectUrl;
        }

        try {
            URI objectUri = URI.create(objectUrl);
            URI endpointUri = URI.create(endpoint);
            String bucketPathPrefix = "/" + bucketName + "/";
            if (objectUri.getHost() == null
                    || endpointUri.getHost() == null
                    || !objectUri.getHost().equalsIgnoreCase(endpointUri.getHost())
                    || objectUri.getRawPath() == null
                    || !objectUri.getRawPath().startsWith(bucketPathPrefix)) {
                return objectUrl;
            }

            String normalizedEndpoint = endpoint.endsWith("/")
                    ? endpoint.substring(0, endpoint.length() - 1)
                    : endpoint;
            String query = objectUri.getRawQuery();
            return query == null
                    ? normalizedEndpoint + objectUri.getRawPath()
                    : normalizedEndpoint + objectUri.getRawPath() + "?" + query;
        } catch (IllegalArgumentException exception) {
            return objectUrl;
        }
    }

    /**
     * Parses the persisted file-content location into the bucket and object key
     * required by the S3 client.
     *
     * <p>New records use {@code bucket/object-key}; old records may contain a
     * complete URL generated by this MinIO deployment. Only URLs belonging to a
     * configured MinIO host and the expected bucket are accepted.</p>
     *
     * @param storagePath persisted storage location
     * @param expectedBucket bucket that is allowed for this operation
     * @return validated S3 object location
     * @throws IllegalArgumentException when the persisted path is malformed or
     *         points outside the expected bucket
     */
    public ObjectLocation parseStoragePath(String storagePath, String expectedBucket) {
        if (storagePath == null || storagePath.isBlank() || expectedBucket == null || expectedBucket.isBlank()) {
            throw new IllegalArgumentException("对象存储路径不能为空");
        }

        String bucketName;
        String objectKey;
        if (storagePath.startsWith("http://") || storagePath.startsWith("https://")) {
            URI storageUri;
            try {
                storageUri = URI.create(storagePath);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("对象存储URL格式不正确", exception);
            }
            if (!isConfiguredMinioHost(storageUri)) {
                throw new IllegalArgumentException("对象存储URL不属于当前MinIO服务");
            }
            String path = storageUri.getPath();
            String prefix = "/" + expectedBucket + "/";
            if (path == null || !path.startsWith(prefix)) {
                throw new IllegalArgumentException("对象存储URL不属于预期存储桶");
            }
            bucketName = expectedBucket;
            objectKey = path.substring(prefix.length());
        } else {
            String prefix = expectedBucket + "/";
            if (!storagePath.startsWith(prefix)) {
                throw new IllegalArgumentException("对象存储路径不属于预期存储桶");
            }
            bucketName = expectedBucket;
            objectKey = storagePath.substring(prefix.length());
        }

        validateObjectKey(objectKey);
        return new ObjectLocation(bucketName, objectKey);
    }

    private boolean isConfiguredMinioHost(URI storageUri) {
        if (storageUri.getHost() == null || storageUri.getUserInfo() != null) {
            return false;
        }
        return isSameHost(storageUri, endpoint) || isSameHost(storageUri, publicEndpoint);
    }

    private boolean isSameHost(URI storageUri, String configuredEndpoint) {
        try {
            URI endpointUri = URI.create(configuredEndpoint);
            return endpointUri.getHost() != null && endpointUri.getHost().equalsIgnoreCase(storageUri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("对象键不合法");
        }
        for (String segment : objectKey.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("对象键不合法");
            }
        }
    }

    /**
     * 上传文件到 MinIO 服务器
     *
     * @param file       要上传的文件
     * @param objectName 在 MinIO 中存储的对象名称
     * @return 文件的访问 URL
     */
    public String upload(MultipartFile file, String objectName) {
        return upload(file, this.bucketName, objectName);
    }

    /**
     * 上传文件到指定的 MinIO 存储桶
     *
     * @param file       要上传的文件
     * @param bucketName 目标存储桶名称
     * @param objectName 在 MinIO 中存储的对象名称
     * @return 文件的访问 URL
     */
    public String upload(MultipartFile file, String bucketName, String objectName) {
        try {
            // 使用缓存的 S3Client
            S3Client client = getS3Client();
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
            RequestBody requestBody = RequestBody.fromInputStream(
                    file.getInputStream(),
                    contentLength
            );

            // 4. 执行上传
            PutObjectResponse response = client.putObject(putObjectRequest, requestBody);
            log.info("文件上传成功到: {}/{}, ETag: {}", bucketName, objectName, response.eTag());

        } catch (Exception e) {
            log.error("上传出错：{}", e.getMessage());
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
        StringBuilder url = new StringBuilder(endpoint);
        url
            .append("/")
            .append(bucketName)
            .append("/")
            .append(objectName);
        log.info("上传成功，文件访问路径: {}", url.toString());
        return url.toString();
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 存储桶名称
     * @return 是否创建成功
     */
    public boolean createBucket(String bucketName) {
        try {
            S3Client client = getS3Client();
            
            // 检查存储桶是否已存在
            if (bucketExists(bucketName)) {
                log.warn("存储桶 {} 已存在", bucketName);
                return true;
            }
            
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
                    
            CreateBucketResponse response = client.createBucket(createBucketRequest);
            log.info("存储桶 {} 创建成功，Location: {}", bucketName, response.location());
            return true;
            
        } catch (Exception e) {
            log.error("创建存储桶 {} 失败: {}", bucketName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查存储桶是否存在
     *
     * @param bucketName 存储桶名称
     * @return 是否存在
     */
    public boolean bucketExists(String bucketName) {
        try {
            S3Client client = getS3Client();
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            client.headBucket(headBucketRequest);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (Exception e) {
            log.error("检查存储桶 {} 是否存在时出错: {}", bucketName, e.getMessage());
            return false;
        }
    }

    /**
     * 删除存储桶
     *
     * @param bucketName 存储桶名称
     * @return 是否删除成功
     */
    public boolean deleteBucket(String bucketName) {
        try {
            S3Client client = getS3Client();
            
            // 检查存储桶是否存在
            if (!bucketExists(bucketName)) {
                log.warn("存储桶 {} 不存在", bucketName);
                return true;
            }
            
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
                    
            client.deleteBucket(deleteBucketRequest);
            log.info("存储桶 {} 删除成功", bucketName);
            return true;
            
        } catch (Exception e) {
            log.error("删除存储桶 {} 失败: {}", bucketName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 列出所有存储桶
     *
     * @return 存储桶名称列表
     */
    public List<String> listBuckets() {
        try {
            S3Client client = getS3Client();
            ListBucketsResponse response = client.listBuckets();
            
            List<String> bucketNames = response.buckets().stream()
                    .map(Bucket::name)
                    .collect(Collectors.toList());
                    
            log.info("获取存储桶列表成功，共 {} 个存储桶", bucketNames.size());
            return bucketNames;
            
        } catch (Exception e) {
            log.error("获取存储桶列表失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 列出存储桶中的对象
     *
     * @param bucketName 存储桶名称
     * @param prefix     对象前缀（可选）
     * @param maxKeys    最大返回数量
     * @return 对象列表
     */
    public List<S3Object> listObjects(String bucketName, String prefix, Integer maxKeys) {
        try {
            S3Client client = getS3Client();
            
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName);
                    
            if (prefix != null && !prefix.isEmpty()) {
                requestBuilder.prefix(prefix);
            }
            
            if (maxKeys != null && maxKeys > 0) {
                requestBuilder.maxKeys(maxKeys);
            }
            
            ListObjectsV2Response response = client.listObjectsV2(requestBuilder.build());
            
            log.info("获取存储桶 {} 中的对象列表成功，共 {} 个对象", bucketName, response.contents().size());
            return response.contents();
            
        } catch (Exception e) {
            log.error("获取存储桶 {} 中的对象列表失败: {}", bucketName, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 删除对象
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 是否删除成功
     */
    public boolean deleteObject(String bucketName, String objectName) {
        try {
            S3Client client = getS3Client();
            
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
                    
            client.deleteObject(deleteObjectRequest);
            log.info("对象 {}/{} 删除成功", bucketName, objectName);
            return true;
            
        } catch (Exception e) {
            log.error("删除对象 {}/{} 失败: {}", bucketName, objectName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取对象的输入流
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 对象的输入流
     */
    public InputStream getObject(String bucketName, String objectName) {
        try {
            S3Client client = getS3Client();
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
                    
            // AWS SDK v2返回ResponseInputStream<GetObjectResponse>，它实现了InputStream接口
            // 可以直接使用作为InputStream
            InputStream response = client.getObject(getObjectRequest);
            log.info("对象 {}/{} 获取成功", bucketName, objectName);
            return response;
            
        } catch (Exception e) {
            log.error("获取对象 {}/{} 失败: {}", bucketName, objectName, e.getMessage(), e);
            throw new RuntimeException("获取对象失败: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a bounded object range without downloading the whole object.
     */
    public InputStream getObjectRange(String bucketName, String objectName, long offset, int length) {
        return getObjectRange(bucketName, objectName, offset, (long) length);
    }

    /**
     * Gets an object byte range. The length is deliberately a long so a valid
     * HTTP range is not constrained by the Java array size limit.
     */
    public InputStream getObjectRange(String bucketName, String objectName, long offset, long length) {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("对象范围参数不合法");
        }
        try {
            String range = "bytes=" + offset + "-" + (offset + length - 1L);
            return getS3Client().getObject(GetObjectRequest.builder()
                    .bucket(bucketName).key(objectName).range(range).build());
        } catch (Exception e) {
            log.error("获取对象范围失败: bucket={}, objectKey={}, offset={}, length={}", bucketName, objectName, offset, length, e);
            throw new RuntimeException("获取对象范围失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传流到对象
     *
     * @param inputStream 输入流
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 文件的访问URL
     */
    public String uploadStream(InputStream inputStream, String bucketName, String objectName) {
        try {
            S3Client client = getS3Client();
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();

            RequestBody requestBody = RequestBody.fromInputStream(inputStream, -1);
            client.putObject(putObjectRequest, requestBody);
            
            log.info("流上传成功到: {}/{}", bucketName, objectName);
            
            StringBuilder url = new StringBuilder(endpoint);
            url.append("/")
                    .append(bucketName)
                    .append("/")
                    .append(objectName);
            return url.toString();
            
        } catch (Exception e) {
            log.error("流上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("流上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查对象是否存在
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @return 是否存在
     */
    public boolean objectExists(String bucketName, String objectName) {
        try {
            S3Client client = getS3Client();
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            client.headObject(headObjectRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("检查对象 {}/{} 是否存在时出错: {}", bucketName, objectName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取对象的预签名URL（用于临时访问）
     *
     * @param bucketName 存储桶名称
     * @param objectName 对象名称
     * @param expiration 过期时间（秒）
     * @return 预签名URL
     */
    public String getPresignedUrl(String bucketName, String objectName, int expiration) {
        try {
            if (expiration <= 0) {
                throw new IllegalArgumentException("预签名URL有效期必须大于0");
            }
            String filename = objectName.substring(objectName.lastIndexOf('/') + 1);
            return presignDownload(bucketName, objectName, filename, Duration.ofSeconds(expiration));
            
        } catch (Exception e) {
            log.error("生成对象 {}/{} 的预签名URL失败: {}", bucketName, objectName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 复制对象
     *
     * @param sourceBucket      源存储桶
     * @param sourceObject      源对象名称
     * @param destinationBucket 目标存储桶
     * @param destinationObject 目标对象名称
     * @return 是否复制成功
     */
    public boolean copyObject(String sourceBucket, String sourceObject, 
                             String destinationBucket, String destinationObject) {
        try {
            S3Client client = getS3Client();
            
            CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
                    .sourceBucket(sourceBucket)
                    .sourceKey(sourceObject)
                    .destinationBucket(destinationBucket)
                    .destinationKey(destinationObject)
                    .build();
                    
            client.copyObject(copyObjectRequest);
            log.info("对象复制成功：{}/{} -> {}/{}", 
                    sourceBucket, sourceObject, destinationBucket, destinationObject);
            return true;
            
        } catch (Exception e) {
            log.error("复制对象失败：{}/{} -> {}/{}, 错误: {}", 
                    sourceBucket, sourceObject, destinationBucket, destinationObject, e.getMessage(), e);
            return false;
        }
    }
}
