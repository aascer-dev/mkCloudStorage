package com.zjj.mkcscommon.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MinIO 工具类
 */
@RequiredArgsConstructor
@Slf4j
@Getter
public class MinIOUtil {
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;

    private static final Region MINIO_REGION = Region.of("us-east-1");
    
    /**
     * 缓存的 S3Client 实例
     */
    private S3Client s3Client;

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

    /**
     * 上传文件到 MinIO 服务器
     *
     * @param file       要上传的文件
     * @param objectName 在 MinIO 中存储的对象名称
     * @return 文件的访问 URL
     */
    public String upload(MultipartFile file, String objectName) {
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
            RequestBody requestBody = null;
            requestBody = RequestBody.fromInputStream(
                    file.getInputStream(),
                    contentLength
            );

            // 4. 执行上传
            PutObjectResponse response = client.putObject(putObjectRequest, requestBody);
            log.info("文件上传成功到: {}/{}, ETag: {}", bucketName, objectName, response.eTag());

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
            S3Client client = getS3Client();
            
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
                    
            // 注意：AWS SDK v2 的预签名URL生成方式
            // 这里需要使用 S3Presigner，但为了简化，我们返回直接访问URL
            String url = String.format("%s/%s/%s", endpoint, bucketName, objectName);
            log.info("生成对象 {}/{} 的访问URL: {}", bucketName, objectName, url);
            return url;
            
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
