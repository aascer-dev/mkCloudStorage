package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.dto.MultipartUploadCompleteRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadInitRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadPartRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadPresignRequest;
import cn.zjj.mkcsmodel.dto.MultipartSecondUploadVerifyRequest;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.UploadTasks;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsmodel.vo.FilePreviewUrlResponse;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadInitResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadPresignResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadStatusResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadedPartResponse;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.FilesService;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UploadTasksService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import com.zjj.mkcscommon.utils.MinIOUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一 服务实现类
 * 扩展功能：文件上传、分片上传、文件去重、秒传、文件夹上传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilesServiceImpl extends ServiceImpl<FilesMapper, Files> implements FilesService {

    private final MinIOUtil minIOUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FileContentsService fileContentsService;
    private final UploadTasksService uploadTasksService;
    private final MultipartUploadPersistenceService multipartUploadPersistenceService;
    private final StorageBucketsService storageBucketsService;

    // Redis key前缀
    private static final String UPLOAD_TASK_PREFIX = "upload:task:";
    private static final String CHUNK_PREFIX = "upload:chunk:";
    private static final String PREVIEW_TICKET_PREFIX = "file:preview-ticket:";
    private static final long PREVIEW_TICKET_TIMEOUT_SECONDS = 5 * 60;
    // 上传时间：24小时
    private static final long UPLOAD_TASK_TIMEOUT = 24 * 60 * 60;
    private static final String FILES_BUCKET = "files";
    private static final int MULTIPART_PART_SIZE = 16 * 1024 * 1024;
    private static final int MULTIPART_MAX_PARTS = 10_000;
    private static final int RANDOM_CHALLENGE_BLOCK_SIZE = 256 * 1024;
    private static final int PRESIGNED_PART_EXPIRY_SECONDS = 15 * 60;
    private static final byte UPLOAD_STATUS_UPLOADING = 1;
    private static final byte UPLOAD_STATUS_COMPLETED = 3;
    private static final byte UPLOAD_STATUS_FAILED = 4;
    private static final byte UPLOAD_STATUS_CANCELLED = 5;

    @Override
    public MultipartUploadInitResponse initMultipartUpload(Long userId, MultipartUploadInitRequest request) {
        int totalParts = calculateMultipartPartCount(request.getFileSize());
        List<UploadTasks> activeTasks = uploadTasksService.list(new LambdaQueryWrapper<UploadTasks>()
                .eq(UploadTasks::getUserId, userId)
                .eq(UploadTasks::getFileHash, request.getFileHash().toLowerCase(Locale.ROOT))
                .eq(UploadTasks::getTotalSize, request.getFileSize())
                .eq(UploadTasks::getFilename, request.getFilename())
                .eq(UploadTasks::getParentId, request.getParentId())
                .eq(UploadTasks::getBucketId, request.getBucketId())
                .in(UploadTasks::getStatus, UPLOAD_STATUS_UPLOADING)
                .gt(UploadTasks::getExpireTime, LocalDateTime.now())
                .orderByDesc(UploadTasks::getUpdatedAt));
        if (!activeTasks.isEmpty()) {
            UploadTasks task = activeTasks.get(0);
            if (task.getMinioUploadId() == null) {
                FileContents challengeContent = fileContentsService.getByContentHash(task.getFileHash());
                if (isSecondUploadChallengeAvailable(challengeContent, task.getTotalSize())) {
                    return secondUploadChallengeResponse(task, challengeContent);
                }
                markMultipartTaskFailed(task, "秒传校验信息已失效");
            } else {
                return multipartInitResponse(task, false, null);
            }
        }

        FileContents existingContent = fileContentsService.getByContentHash(request.getFileHash().toLowerCase(Locale.ROOT));
        if (isSecondUploadChallengeAvailable(existingContent, request.getFileSize())) {
            UploadTasks task = createSecondUploadChallengeTask(userId, request);
            uploadTasksService.save(task);
            return secondUploadChallengeResponse(task, existingContent);
        }

        String objectKey = "contents/" + UUID.randomUUID();
        if (!minIOUtil.bucketExists(FILES_BUCKET) && !minIOUtil.createBucket(FILES_BUCKET)) {
            throw new IllegalStateException("文件存储桶不可用");
        }
        String minioUploadId = minIOUtil.createMultipartUpload(FILES_BUCKET, objectKey, normalizedMimeType(request.getMimeType()));
        UploadTasks task = new UploadTasks();
        task.setUserId(userId);
        task.setBucketId(request.getBucketId());
        task.setParentId(request.getParentId());
        task.setFilename(request.getFilename());
        task.setTotalSize(request.getFileSize());
        task.setUploadedSize(0L);
        task.setChunkSize(MULTIPART_PART_SIZE);
        task.setTotalChunks(totalParts);
        task.setUploadedChunks(0);
        task.setTaskType((byte) 1);
        task.setStatus(UPLOAD_STATUS_UPLOADING);
        task.setFileHash(request.getFileHash().toLowerCase(Locale.ROOT));
        task.setMinioUploadId(minioUploadId);
        task.setObjectKey(objectKey);
        task.setMimeType(normalizedMimeType(request.getMimeType()));
        task.setExpireTime(LocalDateTime.now().plusSeconds(UPLOAD_TASK_TIMEOUT));
        try {
            uploadTasksService.save(task);
        } catch (RuntimeException e) {
            minIOUtil.abortMultipartUpload(FILES_BUCKET, objectKey, minioUploadId);
            throw e;
        }
        return multipartInitResponse(task, false, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResponse verifyMultipartSecondUpload(Long userId, MultipartSecondUploadVerifyRequest request) {
        UploadTasks task = requireActiveMultipartTask(userId, request.getUploadId());
        if (task.getMinioUploadId() != null) {
            throw new IllegalStateException("该上传任务不是秒传验证任务");
        }

        FileContents content = fileContentsService.getByContentHash(task.getFileHash());
        if (!isSecondUploadChallengeAvailable(content, task.getTotalSize())) {
            throw new IllegalStateException("秒传校验信息已失效，请重新初始化上传");
        }
        if (!constantTimeEquals(content.getRandomPositionHash(), request.getChallengeHash())) {
            throw new IllegalStateException("秒传随机切片校验失败");
        }

        content.setReferenceCount(content.getReferenceCount() + 1);
        fileContentsService.updateById(content);
        Files file = createFileRecord(userId, task.getFilename(), task.getTotalSize(), content.getId(),
                task.getParentId(), task.getBucketId(), task.getMimeType(), false);
        task.setStatus(UPLOAD_STATUS_COMPLETED);
        task.setFinalFileId(file.getId());
        task.setUploadedSize(task.getTotalSize());
        task.setUploadedChunks(0);
        task.setErrorMessage(null);
        uploadTasksService.updateById(task);

        return FileUploadResponse.builder().fileId(file.getId()).filename(file.getFilename()).fileSize(file.getSize())
                .isSecondUpload(true).status("duplicate").message("文件内容校验通过，秒传成功").build();
    }

    @Override
    public MultipartUploadPresignResponse presignMultipartPart(Long userId, MultipartUploadPresignRequest request) {
        UploadTasks task = requireActiveMultipartTask(userId, request.getUploadId());
        if (request.getPartNumber() > task.getTotalChunks()) {
            throw new IllegalArgumentException("分片编号超出文件范围");
        }
        return MultipartUploadPresignResponse.builder()
                .partNumber(request.getPartNumber())
                .url(minIOUtil.presignUploadPart(FILES_BUCKET, task.getObjectKey(), task.getMinioUploadId(), request.getPartNumber(),
                        Duration.ofSeconds(PRESIGNED_PART_EXPIRY_SECONDS)))
                .expiresInSeconds(PRESIGNED_PART_EXPIRY_SECONDS)
                .build();
    }

    @Override
    public MultipartUploadStatusResponse getMultipartUploadStatus(Long userId, Long uploadId) {
        UploadTasks task = requireOwnedMultipartTask(userId, uploadId);
        if (task.getStatus() == UPLOAD_STATUS_COMPLETED) {
            return MultipartUploadStatusResponse.builder().uploadId(task.getId()).partSize(task.getChunkSize())
                    .totalParts(task.getTotalChunks()).completed(true).fileId(task.getFinalFileId()).uploadedParts(List.of()).build();
        }
        if (task.getStatus() != UPLOAD_STATUS_UPLOADING) {
            throw new IllegalStateException("上传任务已取消或失败");
        }
        if (task.getMinioUploadId() == null) {
            throw new IllegalStateException("该任务等待秒传随机切片校验");
        }
        List<Part> parts = minIOUtil.listMultipartUploadParts(FILES_BUCKET, task.getObjectKey(), task.getMinioUploadId());
        return MultipartUploadStatusResponse.builder().uploadId(task.getId()).partSize(task.getChunkSize())
                .totalParts(task.getTotalChunks()).completed(false)
                .uploadedParts(parts.stream().map(part -> MultipartUploadedPartResponse.builder()
                        .partNumber(part.partNumber()).etag(part.eTag()).size(part.size()).build()).toList()).build();
    }

    @Override
    public FileUploadResponse completeMultipartUpload(Long userId, MultipartUploadCompleteRequest request) {
        UploadTasks task = requireActiveMultipartTask(userId, request.getUploadId());
        List<Part> storageParts = minIOUtil.listMultipartUploadParts(FILES_BUCKET, task.getObjectKey(), task.getMinioUploadId());
        Map<Integer, String> suppliedParts = new HashMap<>();
        for (MultipartUploadPartRequest part : request.getParts()) {
            if (suppliedParts.put(part.getPartNumber(), part.getEtag()) != null) {
                throw new IllegalArgumentException("分片编号不能重复");
            }
        }
        if (storageParts.size() != task.getTotalChunks() || suppliedParts.size() != task.getTotalChunks()) {
            throw new IllegalStateException("分片上传不完整");
        }
        List<CompletedPart> completedParts = new ArrayList<>();
        for (int partNumber = 1; partNumber <= task.getTotalChunks(); partNumber++) {
            Part storagePart = null;
            for (Part candidate : storageParts) {
                if (candidate.partNumber() == partNumber) {
                    storagePart = candidate;
                    break;
                }
            }
            if (storagePart == null) {
                throw new IllegalStateException("缺少第" + partNumber + "个分片");
            }
            if (!Objects.equals(storagePart.eTag(), suppliedParts.get(partNumber))) {
                throw new IllegalStateException("分片ETag校验失败");
            }
            completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(storagePart.eTag()).build());
        }
        minIOUtil.completeMultipartUpload(FILES_BUCKET, task.getObjectKey(), task.getMinioUploadId(), completedParts);
        if (minIOUtil.getObjectSize(FILES_BUCKET, task.getObjectKey()) != task.getTotalSize()) {
            minIOUtil.deleteObject(FILES_BUCKET, task.getObjectKey());
            markMultipartTaskFailed(task, "合并后文件大小校验失败");
            throw new IllegalStateException("合并后文件大小校验失败");
        }
        Object[] randomChecksum;
        try {
            randomChecksum = generateMultipartRandomPositionChecksum(task.getObjectKey(), task.getTotalSize());
        } catch (RuntimeException e) {
            deleteFailedMultipartObject(task);
            markMultipartTaskFailed(task, "生成秒传校验信息失败");
            throw new IllegalStateException("生成秒传校验信息失败", e);
        }
        try {
            FileUploadResponse response = multipartUploadPersistenceService.persist(task, (Long) randomChecksum[0],
                    (Integer) randomChecksum[1], (String) randomChecksum[2]);
            return response;
        } catch (RuntimeException e) {
            deleteFailedMultipartObject(task);
            markMultipartTaskFailed(task, "文件元数据保存失败");
            throw e;
        }
    }

    @Override
    public void cancelMultipartUpload(Long userId, Long uploadId) {
        UploadTasks task = requireOwnedMultipartTask(userId, uploadId);
        if (task.getStatus() == UPLOAD_STATUS_COMPLETED) {
            throw new IllegalStateException("已完成的上传不能取消");
        }
        if (task.getMinioUploadId() != null) {
            minIOUtil.abortMultipartUpload(FILES_BUCKET, task.getObjectKey(), task.getMinioUploadId());
        }
        task.setStatus(UPLOAD_STATUS_CANCELLED);
        task.setErrorMessage(null);
        uploadTasksService.updateById(task);
    }

    private void deleteFailedMultipartObject(UploadTasks task) {
        try {
            minIOUtil.deleteObject(FILES_BUCKET, task.getObjectKey());
        } catch (RuntimeException cleanupError) {
            log.warn("清理失败的 Multipart 对象失败: taskId={}", task.getId(), cleanupError);
        }
    }

    private void markMultipartTaskFailed(UploadTasks task, String message) {
        task.setStatus(UPLOAD_STATUS_FAILED);
        task.setErrorMessage(message);
        uploadTasksService.updateById(task);
    }

    private UploadTasks requireActiveMultipartTask(Long userId, Long uploadId) {
        UploadTasks task = requireOwnedMultipartTask(userId, uploadId);
        if (task.getStatus() != UPLOAD_STATUS_UPLOADING || task.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("上传任务已过期或不可用");
        }
        return task;
    }

    private UploadTasks requireOwnedMultipartTask(Long userId, Long uploadId) {
        UploadTasks task = uploadTasksService.getById(uploadId);
        if (task == null) {
            throw new IllegalArgumentException("上传任务不存在");
        }
        if (!Objects.equals(task.getUserId(), userId)) {
            throw new IllegalStateException("无权访问此上传任务");
        }
        return task;
    }

    private int calculateMultipartPartCount(long fileSize) {
        long parts = (fileSize + MULTIPART_PART_SIZE - 1L) / MULTIPART_PART_SIZE;
        if (parts > MULTIPART_MAX_PARTS) {
            throw new IllegalArgumentException("文件超过Multipart上传最大分片数限制");
        }
        return (int) parts;
    }

    private MultipartUploadInitResponse multipartInitResponse(UploadTasks task, boolean instantUpload, Long fileId) {
        return MultipartUploadInitResponse.builder().uploadId(task.getId()).partSize(task.getChunkSize())
                .totalParts(task.getTotalChunks()).instantUpload(instantUpload).fileId(fileId).build();
    }

    private MultipartUploadInitResponse secondUploadChallengeResponse(UploadTasks task, FileContents content) {
        return MultipartUploadInitResponse.builder().uploadId(task.getId()).instantUpload(false)
                .secondUploadChallenge(true).challengeOffset(content.getRandomOffset())
                .challengeLength(content.getRandomLength()).build();
    }

    private UploadTasks createSecondUploadChallengeTask(Long userId, MultipartUploadInitRequest request) {
        UploadTasks task = new UploadTasks();
        task.setUserId(userId);
        task.setBucketId(request.getBucketId());
        task.setParentId(request.getParentId());
        task.setFilename(request.getFilename());
        task.setTotalSize(request.getFileSize());
        task.setUploadedSize(0L);
        task.setUploadedChunks(0);
        task.setTaskType((byte) 1);
        task.setStatus(UPLOAD_STATUS_UPLOADING);
        task.setFileHash(request.getFileHash().toLowerCase(Locale.ROOT));
        task.setMimeType(normalizedMimeType(request.getMimeType()));
        task.setExpireTime(LocalDateTime.now().plusSeconds(UPLOAD_TASK_TIMEOUT));
        return task;
    }

    private boolean isSecondUploadChallengeAvailable(FileContents content, Long expectedSize) {
        return content != null && content.getStatus() == 1 && Objects.equals(content.getSize(), expectedSize)
                && content.getRandomOffset() != null && content.getRandomOffset() >= 0
                && content.getRandomLength() != null && content.getRandomLength() > 0
                && content.getRandomLength() <= RANDOM_CHALLENGE_BLOCK_SIZE
                && content.getRandomOffset() <= expectedSize - content.getRandomLength()
                && content.getRandomPositionHash() != null && content.getRandomPositionHash().matches("^[a-fA-F0-9]{32}$");
    }

    private boolean constantTimeEquals(String expectedHash, String suppliedHash) {
        return expectedHash != null && suppliedHash != null && MessageDigest.isEqual(
                expectedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                suppliedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private String normalizedMimeType(String mimeType) {
        return mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
    }

    /**
     * 检查文件是否存在（秒传检查）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResponse checkFileExists(
            Long userId,
            String filename,
            String contentHash,
            Long fileSize,
            Long parentId,
            Long bucketId,
            String mimeType) {

        // 1. 检查是否已存在相同hash的文件内容
        FileContents existingContent = fileContentsService.getByContentHash(contentHash);
        
        if (existingContent != null && existingContent.getStatus() == 1) {
            // 2. 检查当前用户是否已有相同相同文件夹的相同名称的文件
            LambdaQueryWrapper<Files> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Files::getOwnerId, userId)
                    .eq(Files::getFilename, filename)
                    .eq(Files::getParentId, parentId)
                    .eq(Files::getStatus, 1);
            
            Files existingFile = baseMapper.selectOne(wrapper);
            
            if (existingFile != null ) {
                // 文件已存在，支持秒传，这里可能会出现一些指向软删除的（status为0），这个我们在这里也是正常认可的
                //构造秒传响应
                return FileUploadResponse.builder()
                        .fileId(existingFile.getId())
                        .filename(filename)
                        .fileSize(fileSize)
                        .isSecondUpload(true)
                        .status("duplicate")
                        .message("文件已存在，支持秒传")
                        .build();
            } else {
                // 内容相同但文件名不同，创建新的文件记录指向相同内容
                existingContent.setReferenceCount(existingContent.getReferenceCount() + 1);
                fileContentsService.updateById(existingContent);
                Files newFile = createFileRecord(userId, filename, fileSize, existingContent.getId(), 
                        parentId, bucketId, mimeType, false);
                
                return FileUploadResponse.builder()
                        .fileId(newFile.getId())
                        .filename(filename)
                        .fileSize(fileSize)
                        .isSecondUpload(true)
                        .status("duplicate")
                        .message("文件内容已存在，创建新记录")
                        .build();
            }
        }

        // 文件不存在
        return FileUploadResponse.builder()
                .filename(filename)
                .fileSize(fileSize)
                .isSecondUpload(false)
                .status("not_found")
                .message("文件不存在，需要上传")
                .build();
    }

    /**
     * 单文件上传（包含秒传检查）
     * 设计说明：
     * - 前端直接上传文件，不计算hash（避免安全隐患）
     * - 后端计算hash并自动处理秒传
     * - 响应中包含isSecondUpload标志
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResponse uploadFile(
            Long userId,
            MultipartFile file,
            Long parentId,
            Long bucketId) {

        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();
        String mimeType = file.getContentType();
        String uploadedObjectName = null;
        
        log.info("开始单文件上传: userId={}, filename={}, size={}", userId, filename, fileSize);

        try {
            // 1. 验证文件有效性
            if (file.isEmpty()) {
                log.warn("上传的文件为空: filename={}", filename);
                throw new RuntimeException("文件为空");
            }
            
            if (fileSize <= 0) {
                log.warn("上传的文件大小无效: filename={}, size={}", filename, fileSize);
                throw new RuntimeException("文件大小无效");
            }

            // 2. 计算文件hash
            String contentHash;
            try {
                contentHash = calculateFileHash(file.getInputStream());
                log.debug("文件hash计算成功: filename={}, hash={}", filename, contentHash);
            } catch (Exception e) {
                log.error("计算文件hash失败: filename={}, error={}", filename, e.getMessage());
                throw new RuntimeException("计算文件hash失败: " + e.getMessage());
            }

            // 3. 检查文件内容是否已存在（秒传）
            FileContents existingContent = fileContentsService.getByContentHash(contentHash);
            
            Long contentId;
            boolean isSecondUpload = false;
            
            if (existingContent != null && existingContent.getStatus() == 1) {
                // 文件内容已存在，进行双重验证（秒传安全检查）
                log.info("检测到文件秒传候选: filename={}, existingContentId={}", filename, existingContent.getId());
                
                // 验证随机位置校验（增强安全性，防止虚假秒传）
                try {
                    if (!verifySecondUploadRandomPosition(file, existingContent)) {
                        log.warn("秒传安全验证失败，拒绝秒传: filename={}, contentId={}", filename, existingContent.getId());
                        throw new RuntimeException("秒传验证失败: 文件内容校验不匹配，可能是伪造的请求");
                    }
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("秒传验证异常: filename={}, error={}", filename, e.getMessage());
                    throw new RuntimeException("秒传验证异常: " + e.getMessage());
                }
                
                // 验证通过，执行秒传
                log.info("秒传验证通过，接受秒传: filename={}, existingContentId={}", filename, existingContent.getId());
                contentId = existingContent.getId();
                existingContent.setReferenceCount(existingContent.getReferenceCount() + 1);
                fileContentsService.updateById(existingContent);
                isSecondUpload = true;
                log.debug("已更新文件引用计数: contentId={}, newCount={}", contentId, existingContent.getReferenceCount());
                
            } else {
                // 上传文件到MinIO
                log.info("开始上传文件到MinIO: filename={}", filename);
                String objectName = generateObjectName(filename);
                try {
                    minIOUtil.upload(file, FILES_BUCKET, objectName);
                    uploadedObjectName = objectName;
                    log.info("文件上传到MinIO成功: filename={}", filename);
                } catch (Exception e) {
                    log.error("上传文件到MinIO失败: filename={}, error={}", filename, e.getMessage());
                    throw new RuntimeException("上传文件失败: " + e.getMessage());
                }

                // 生成随机位置校验参数（增强秒传安全性）
                Object[] randomChecksum = null;
                try {
                    randomChecksum = generateRandomPositionChecksum(file, fileSize);
                    log.info("已生成随机位置校验: offset={}, length={}", randomChecksum[0], randomChecksum[1]);
                } catch (Exception e) {
                    log.warn("生成随机位置校验失败，继续上传但不保存校验信息: filename={}, error={}", filename, e.getMessage());
                }

                // 创建文件内容记录
                FileContents fileContent = new FileContents();
                fileContent.setContentHash(contentHash);
                fileContent.setSize(fileSize);
                fileContent.setStoragePath(FILES_BUCKET + "/" + objectName);
                fileContent.setMimeType(mimeType);
                fileContent.setStatus((byte) 1);
                fileContent.setReferenceCount(1);
                fileContent.setCreatedAt(LocalDateTime.now());
                
                // 保存随机位置校验信息
                if (randomChecksum != null) {
                    fileContent.setRandomOffset((Long) randomChecksum[0]);
                    fileContent.setRandomLength((Integer) randomChecksum[1]);
                    fileContent.setRandomPositionHash((String) randomChecksum[2]);
                }
                
                try {
                    fileContentsService.save(fileContent);
                    contentId = fileContent.getId();
                    log.info("文件内容记录已创建: contentId={}, hash={}, randomChecksum={}", 
                             contentId, contentHash, randomChecksum != null);
                } catch (Exception e) {
                    log.error("创建文件内容记录失败: filename={}, error={}", filename, e.getMessage());
                    throw new RuntimeException("创建文件记录失败: " + e.getMessage());
                }
            }

            // 4. 创建文件元数据记录
            Files fileRecord;
            try {
                fileRecord = createFileRecord(userId, filename, fileSize, contentId, 
                        parentId, bucketId, mimeType, false);
                log.info("文件记录已创建: fileId={}, filename={}", fileRecord.getId(), filename);
            } catch (Exception e) {
                log.error("创建文件记录失败: filename={}, error={}", filename, e.getMessage());
                throw new RuntimeException("创建文件记录失败: " + e.getMessage());
            }

            String message = isSecondUpload ? "文件已存在，秒传成功" : "文件上传成功";
            log.info("文件上传完成: fileId={}, isSecondUpload={}", fileRecord.getId(), isSecondUpload);

            return FileUploadResponse.builder()
                    .fileId(fileRecord.getId())
                    .filename(filename)
                    .fileSize(fileSize)
                    .isSecondUpload(isSecondUpload)
                    .status(isSecondUpload ? "duplicate" : "success")
                    .message(message)
                    .build();

        } catch (Exception e) {
            if (uploadedObjectName != null) {
                minIOUtil.deleteObject(FILES_BUCKET, uploadedObjectName);
            }
            log.error("文件上传失败: filename={}, userId={}, error={}", filename, userId, e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 初始化分片上传
     * 检查秒传、生成上传ID、记录上传任务信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChunkUploadResponse initChunkUpload(
            Long userId,
            String filename,
            Long fileSize,
            String fileHash,
            Integer totalChunks,
            Long parentId,
            Long bucketId,
            String mimeType) {

        log.info("初始化分片上传: userId={}, filename={}, fileSize={}, totalChunks={}", 
                 userId, filename, fileSize, totalChunks);

        try {
            // 1. 参数验证
            if (filename == null || filename.trim().isEmpty()) {
                log.warn("文件名为空");
                throw new RuntimeException("文件名不能为空");
            }
            
            if (fileSize <= 0) {
                log.warn("文件大小无效: fileSize={}", fileSize);
                throw new RuntimeException("文件大小必须大于0");
            }
            
            if (totalChunks <= 0) {
                log.warn("分片数量无效: totalChunks={}", totalChunks);
                throw new RuntimeException("分片数量必须大于0");
            }
            
            if (fileHash == null || fileHash.trim().isEmpty()) {
                log.warn("文件hash为空");
                throw new RuntimeException("文件hash不能为空");
            }

            // 2. 检查文件是否已存在（秒传）
            FileContents existingContent = fileContentsService.getByContentHash(fileHash);
            if (existingContent != null && existingContent.getStatus() == 1) {
                log.info("检测到分片上传秒传: filename={}, existingContentId={}", filename, existingContent.getId());
                existingContent.setReferenceCount(existingContent.getReferenceCount() + 1);
                fileContentsService.updateById(existingContent);
                
                // 直接创建文件记录，返回完成状态
                Files fileRecord = createFileRecord(userId, filename, fileSize, existingContent.getId(), 
                        parentId, bucketId, mimeType, false);
                
                return ChunkUploadResponse.builder()
                        .uploadId(UUID.randomUUID().toString())
                        .totalChunks(totalChunks)
                        .uploadedChunks(new ArrayList<>())
                        .progress(100)
                        .isComplete(true)
                        .fileId(fileRecord.getId())
                        .message("文件已存在，分片上传秒传成功")
                        .build();
            }

            // 3. 生成上传任务ID
            String uploadId = UUID.randomUUID().toString();
            log.info("已生成上传任务ID: uploadId={}", uploadId);

            // 4. 构建上传任务信息
            Map<String, Object> uploadTask = new HashMap<>();
            uploadTask.put("uploadId", uploadId);
            uploadTask.put("userId", userId);
            uploadTask.put("filename", filename);
            uploadTask.put("fileSize", fileSize);
            uploadTask.put("fileHash", fileHash);
            uploadTask.put("totalChunks", totalChunks);
            uploadTask.put("parentId", parentId);
            uploadTask.put("bucketId", bucketId);
            uploadTask.put("mimeType", mimeType != null ? mimeType : "application/octet-stream");
            uploadTask.put("uploadedChunks", new HashSet<Integer>());
            uploadTask.put("createdTime", System.currentTimeMillis());

            // 5. 保存上传任务到Redis
            try {
                redisTemplate.opsForHash().putAll(UPLOAD_TASK_PREFIX + uploadId, uploadTask);
                redisTemplate.expire(UPLOAD_TASK_PREFIX + uploadId, UPLOAD_TASK_TIMEOUT, TimeUnit.SECONDS);
                log.info("上传任务已保存到Redis: uploadId={}, expiryTime={}s", uploadId, UPLOAD_TASK_TIMEOUT);
            } catch (Exception e) {
                log.error("保存上传任务到Redis失败: uploadId={}, error={}", uploadId, e.getMessage());
                throw new RuntimeException("初始化上传失败: " + e.getMessage());
            }

            log.info("分片上传初始化完成: uploadId={}", uploadId);

            return ChunkUploadResponse.builder()
                    .uploadId(uploadId)
                    .totalChunks(totalChunks)
                    .uploadedChunks(new ArrayList<>())
                    .progress(0)
                    .isComplete(false)
                    .message("分片上传初始化成功")
                    .build();

        } catch (Exception e) {
            log.error("初始化分片上传失败: filename={}, error={}", filename, e.getMessage(), e);
            throw new RuntimeException("初始化分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传单个分片
     * 支持重复上传幂等性、分片校验
     */
    @Override
    public ChunkUploadResponse uploadChunk(
            Long userId,
            String uploadId,
            Integer chunkIndex,
            MultipartFile chunk,
            String chunkHash,
            Long randomOffset,
            Integer randomLength,
            String randomHash) {

        try {
            // 1. 验证上传任务存在性
            Map<Object, Object> uploadTask = redisTemplate.opsForHash().entries(UPLOAD_TASK_PREFIX + uploadId);
            if (uploadTask.isEmpty()) {
                log.warn("上传任务不存在或已过期: uploadId={}", uploadId);
                throw new RuntimeException("上传任务不存在或已过期，请重新初始化");
            }

            // 2. 权限验证
            Long taskUserId = Long.valueOf(uploadTask.get("userId").toString());
            if (!taskUserId.equals(userId)) {
                log.warn("权限验证失败: 用户 {} 尝试上传用户 {} 的文件", userId, taskUserId);
                throw new RuntimeException("无权限上传此文件");
            }

            Integer totalChunks = Integer.valueOf(uploadTask.get("totalChunks").toString());
            
            // 3. 验证分片索引有效性
            if (chunkIndex < 0 || chunkIndex >= totalChunks) {
                log.warn("分片索引无效: chunkIndex={}, totalChunks={}", chunkIndex, totalChunks);
                throw new RuntimeException("分片索引无效");
            }

            // 4. 验证分片大小（可选）
            long chunkSize = chunk.getSize();
            if (chunkSize == 0) {
                log.warn("分片为空: uploadId={}, chunkIndex={}", uploadId, chunkIndex);
                throw new RuntimeException("分片数据为空");
            }

            // 5. 随机位置校验（如果提供了校验参数）
            if (randomOffset != null && randomLength != null && randomHash != null) {
                log.debug("进行随机位置校验: uploadId={}, offset={}, length={}", uploadId, randomOffset, randomLength);
                verifyRandomPosition(chunk.getInputStream(), randomOffset, randomLength, randomHash, uploadId, chunkIndex);
            }

            // 6. 获取已上传分片集合
            @SuppressWarnings("unchecked")
            Set<Integer> uploadedChunks = (Set<Integer>) uploadTask.get("uploadedChunks");
            if (uploadedChunks == null) {
                uploadedChunks = new HashSet<>();
            }

            // 7. 检查分片是否已经上传过（幂等性）
            if (uploadedChunks.contains(chunkIndex)) {
                log.info("分片已经上传过，跳过重复上传: uploadId={}, chunkIndex={}", uploadId, chunkIndex);
                // 直接返回成功
            } else {
                // 8. 上传分片到MinIO
                String chunkObjectName = generateChunkObjectName(uploadId, chunkIndex);
                try {
                    minIOUtil.upload(chunk, "chunks", chunkObjectName);
                    log.debug("分片上传成功: uploadId={}, chunkIndex={}, size={}", uploadId, chunkIndex, chunkSize);
                } catch (Exception e) {
                    log.error("分片上传到MinIO失败: uploadId={}, chunkIndex={}, error={}", uploadId, chunkIndex, e.getMessage());
                    throw new RuntimeException("分片上传失败: " + e.getMessage());
                }

                // 9. 记录已上传的分片
                uploadedChunks.add(chunkIndex);
            }

            // 10. 更新Redis中的上传任务
            redisTemplate.opsForHash().put(UPLOAD_TASK_PREFIX + uploadId, "uploadedChunks", uploadedChunks);
            redisTemplate.expire(UPLOAD_TASK_PREFIX + uploadId, UPLOAD_TASK_TIMEOUT, TimeUnit.SECONDS);

            // 11. 计算进度
            int progress = (uploadedChunks.size() * 100) / totalChunks;
            boolean isComplete = uploadedChunks.size() == totalChunks;

            log.info("分片上传状态: uploadId={}, chunkIndex={}, progress={}%, isComplete={}", 
                     uploadId, chunkIndex, progress, isComplete);

            return ChunkUploadResponse.builder()
                    .uploadId(uploadId)
                    .chunkIndex(chunkIndex)
                    .totalChunks(totalChunks)
                    .uploadedChunks(new ArrayList<>(uploadedChunks))
                    .progress(progress)
                    .isComplete(isComplete)
                    .message(isComplete ? "所有分片已上传" : "分片上传成功")
                    .build();

        } catch (Exception e) {
            log.error("分片上传失败: uploadId={}, chunkIndex={}, error={}", uploadId, chunkIndex, e.getMessage(), e);
            throw new RuntimeException("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 完成分片上传
     * 合并所有分片，创建文件记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResponse completeChunkUpload(
            Long userId,
            String uploadId,
            String fileHash) {

        log.info("开始完成分片上传: uploadId={}, userId={}", uploadId, userId);
        String mergedObjectName = null;
        try {
            // 1. 获取上传任务信息
            Map<Object, Object> uploadTask = redisTemplate.opsForHash().entries(UPLOAD_TASK_PREFIX + uploadId);
            if (uploadTask.isEmpty()) {
                log.warn("上传任务不存在或已过期: uploadId={}", uploadId);
                throw new RuntimeException("上传任务不存在或已过期");
            }

            // 2. 权限验证
            Long taskUserId = Long.valueOf(uploadTask.get("userId").toString());
            if (!taskUserId.equals(userId)) {
                log.warn("权限验证失败: 用户 {} 尝试完成用户 {} 的上传", userId, taskUserId);
                throw new RuntimeException("无权限完成此上传");
            }

            // 3. 提取上传任务信息
            String filename = uploadTask.get("filename").toString();
            Long fileSize = Long.valueOf(uploadTask.get("fileSize").toString());
            Integer totalChunks = Integer.valueOf(uploadTask.get("totalChunks").toString());
            Long parentId = uploadTask.get("parentId") != null ? 
                    Long.valueOf(uploadTask.get("parentId").toString()) : null;
            Long bucketId = uploadTask.get("bucketId") != null ? 
                    Long.valueOf(uploadTask.get("bucketId").toString()) : null;
            String mimeType = uploadTask.get("mimeType").toString();
            String taskFileHash = uploadTask.get("fileHash").toString();

            // 4. 验证分片完整性
            @SuppressWarnings("unchecked")
            Set<Integer> uploadedChunks = (Set<Integer>) uploadTask.get("uploadedChunks");
            if (uploadedChunks == null || uploadedChunks.size() != totalChunks) {
                log.warn("分片上传不完整: uploadId={}, uploadedChunks={}, totalChunks={}", 
                         uploadId, uploadedChunks == null ? 0 : uploadedChunks.size(), totalChunks);
                throw new RuntimeException("分片上传不完整，请上传所有分片");
            }
            
            // 验证分片顺序是否完整（0到totalChunks-1）
            for (int i = 0; i < totalChunks; i++) {
                if (!uploadedChunks.contains(i)) {
                    log.warn("缺少分片: uploadId={}, missingChunk={}", uploadId, i);
                    throw new RuntimeException("缺少第" + (i + 1) + "个分片");
                }
            }

            log.info("分片完整性验证通过: uploadId={}, totalChunks={}", uploadId, totalChunks);

            // 5. 合并分片
            mergedObjectName = generateObjectName(filename);
            log.info("开始合并分片: uploadId={}, mergedObjectName={}", uploadId, mergedObjectName);
            mergeChunks(uploadId, totalChunks, mergedObjectName);
            log.info("分片合并成功: uploadId={}", uploadId);

            // 6. 生成随机位置校验参数（分片上传也要进行安全校验）
            Object[] randomChecksum = null;
            try {
                // 从MinIO读回合并后的文件来生成随机位置校验
                // 注意：这会增加一次网络I/O，但为了安全性值得
                InputStream mergedFileStream = minIOUtil.getObject("files", mergedObjectName);
                if (mergedFileStream != null) {
                    randomChecksum = generateRandomPositionChecksum(mergedFileStream, fileSize);
                    log.info("已为合并文件生成随机位置校验: offset={}, length={}", 
                             randomChecksum[0], randomChecksum[1]);
                } else {
                    log.warn("无法从MinIO读回合并文件，跳过随机位置校验生成: uploadId={}", uploadId);
                }
            } catch (Exception e) {
                log.warn("生成随机位置校验失败，继续完成上传但不保存校验信息: uploadId={}, error={}", 
                         uploadId, e.getMessage());
            }

            // 7. 创建文件内容记录
            FileContents fileContent = new FileContents();
            fileContent.setContentHash(taskFileHash);
            fileContent.setSize(fileSize);
            fileContent.setStoragePath("files/" + mergedObjectName);
            fileContent.setMimeType(mimeType);
            fileContent.setStatus((byte) 1);
            fileContent.setReferenceCount(1);
            
            // 保存随机位置校验信息
            if (randomChecksum != null) {
                fileContent.setRandomOffset((Long) randomChecksum[0]);
                fileContent.setRandomLength((Integer) randomChecksum[1]);
                fileContent.setRandomPositionHash((String) randomChecksum[2]);
            }
            
            fileContentsService.save(fileContent);
            log.info("文件内容记录已创建: contentId={}, hash={}, randomChecksum={}", 
                     fileContent.getId(), taskFileHash, randomChecksum != null);

            // 8. 创建文件元数据记录
            Files fileRecord = createFileRecord(userId, filename, fileSize, fileContent.getId(), 
                    parentId, bucketId, mimeType, false);
            log.info("文件记录已创建: fileId={}, filename={}", fileRecord.getId(), filename);

            // 9. 清理Redis中的临时数据
            try {
                redisTemplate.delete(UPLOAD_TASK_PREFIX + uploadId);
                log.debug("已清理上传任务记录: uploadId={}", uploadId);
            } catch (Exception e) {
                log.warn("清理Redis记录失败: {}", e.getMessage());
            }

            log.info("分片上传完成: uploadId={}, fileId={}", uploadId, fileRecord.getId());

            return FileUploadResponse.builder()
                    .fileId(fileRecord.getId())
                    .filename(filename)
                    .fileSize(fileSize)
                    .isSecondUpload(false)
                    .status("success")
                    .message("文件上传完成")
                    .build();

        } catch (Exception e) {
            if (mergedObjectName != null) {
                minIOUtil.deleteObject(FILES_BUCKET, mergedObjectName);
            }
            log.error("完成分片上传失败: uploadId={}, error={}", uploadId, e.getMessage(), e);
            throw new RuntimeException("完成分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 取消分片上传
     * 清理临时数据和已上传的分片
     */
    @Override
    public void cancelChunkUpload(Long userId, String uploadId) {
        log.info("取消分片上传: uploadId={}, userId={}", uploadId, userId);
        
        try {
            // 1. 获取上传任务信息
            Map<Object, Object> uploadTask = redisTemplate.opsForHash().entries(UPLOAD_TASK_PREFIX + uploadId);
            if (uploadTask.isEmpty()) {
                log.warn("上传任务不存在或已过期: uploadId={}", uploadId);
                return;
            }

            // 2. 权限验证
            Long taskUserId = Long.valueOf(uploadTask.get("userId").toString());
            if (!taskUserId.equals(userId)) {
                log.warn("权限验证失败: 用户 {} 尝试取消用户 {} 的上传", userId, taskUserId);
                throw new RuntimeException("无权限取消此上传");
            }

            Integer totalChunks = Integer.valueOf(uploadTask.get("totalChunks").toString());

            // 3. 从MinIO删除已上传的分片
            @SuppressWarnings("unchecked")
            Set<Integer> uploadedChunks = (Set<Integer>) uploadTask.get("uploadedChunks");
            if (uploadedChunks != null && !uploadedChunks.isEmpty()) {
                log.info("开始清理已上传的分片: uploadId={}, count={}", uploadId, uploadedChunks.size());
                for (Integer chunkIndex : uploadedChunks) {
                    try {
                        String chunkObjectName = generateChunkObjectName(uploadId, chunkIndex);
                        minIOUtil.deleteObject("chunks", chunkObjectName);
                        log.debug("已删除分片: {}", chunkObjectName);
                    } catch (Exception e) {
                        log.warn("删除分片失败: chunkIndex={}, error={}", chunkIndex, e.getMessage());
                    }
                }
            }

            // 4. 从Redis删除上传任务记录
            try {
                redisTemplate.delete(UPLOAD_TASK_PREFIX + uploadId);
                log.info("已删除上传任务记录: uploadId={}", uploadId);
            } catch (Exception e) {
                log.warn("删除Redis记录失败: {}", e.getMessage());
            }

            log.info("分片上传已取消: uploadId={}", uploadId);

        } catch (Exception e) {
            log.error("取消分片上传失败: uploadId={}, error={}", uploadId, e.getMessage(), e);
            throw new RuntimeException("取消分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 创建文件夹
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Files createFolder(
            Long userId,
            String folderName,
            Long parentId,
            Long bucketId) {

        Files folder = new Files();
        folder.setOwnerId(userId);
        folder.setFilename(folderName);
        folder.setParentId(parentId);
        folder.setBucketId(bucketId);
        folder.setIsFolder(true);
        folder.setSize(0L);
        folder.setStatus((byte) 1);
        folder.setPath(buildPath(parentId, folderName));

        baseMapper.insert(folder);
        return folder;
    }

    /**
     * 批量创建文件夹（支持嵌套路径）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Files batchCreateFolders(
            Long userId,
            String folderPath,
            Long parentId,
            Long bucketId) {

        String[] folders = folderPath.split("/");
        Long currentParentId = parentId;

        Files lastFolder = null;
        for (String folderName : folders) {
            if (folderName.isEmpty()) continue;

            // 检查文件夹是否已存在
            LambdaQueryWrapper<Files> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Files::getOwnerId, userId)
                    .eq(Files::getFilename, folderName)
                    .eq(Files::getParentId, currentParentId)
                    .eq(Files::getIsFolder, true)
                    .eq(Files::getStatus, 1);

            Files existingFolder = baseMapper.selectOne(wrapper);
            if (existingFolder != null) {
                lastFolder = existingFolder;
                currentParentId = existingFolder.getId();
            } else {
                lastFolder = createFolder(userId, folderName, currentParentId, bucketId);
                currentParentId = lastFolder.getId();
            }
        }

        return lastFolder;
    }

    /**
     * 获取文件详情
     */
    @Override
    public Files getFileInfo(Long userId, Long fileId) {
        Files file = baseMapper.selectById(fileId);
        if (file == null || !file.getOwnerId().equals(userId) || file.getStatus() != 1) {
            return null;
        }
        return file;
    }

    /**
     * 获取文件夹内容
     */
    @Override
    public List<Files> getFolderContents(Long userId, Long folderId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Files> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Files::getOwnerId, userId)
                .eq(Files::getStatus, 1)
                .orderByDesc(Files::getIsFolder)
                .orderByDesc(Files::getCreatedAt);

        // The HTTP API reserves 0 for the virtual root directory. Persisted root
        // records use a NULL parent_id, so applying an equality predicate here
        // would make a root listing permanently empty.
        if (folderId == null || folderId == 0L) {
            wrapper.isNull(Files::getParentId);
        } else {
            wrapper.eq(Files::getParentId, folderId);
        }

        int offset = (pageNum - 1) * pageSize;
        return baseMapper.selectList(wrapper.last("LIMIT " + offset + ", " + pageSize));
    }

    /**
     * 删除文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long userId, Long fileId) {
        Files file = getFileInfo(userId, fileId);
        Assert.notNull(file, "文件不存在");
        softDeleteSubtree(file);
    }

    /**
     * 批量删除文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteFiles(Long userId, List<Long> fileIds) {
        for (Long fileId : fileIds) {
            deleteFile(userId, fileId);
        }
    }

    @Override
    public List<Files> getRecycleBinFiles(Long userId) {
        return baseMapper.selectList(new LambdaQueryWrapper<Files>()
                .eq(Files::getOwnerId, userId)
                .eq(Files::getStatus, 0)
                .orderByDesc(Files::getUpdatedAt)
                .orderByDesc(Files::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreFiles(Long userId, List<Long> fileIds) {
        for (Files file : recycleBinSelectionRoots(userId, fileIds)) {
            ensureParentIsAvailableForRestore(userId, file);
            restoreSubtree(file);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void permanentlyDeleteFiles(Long userId, List<Long> fileIds) {
        for (Files file : recycleBinSelectionRoots(userId, fileIds)) {
            ensureSubtreeIsDeleted(file);
            permanentlyDeleteSubtree(file);
        }
    }

    private void softDeleteSubtree(Files file) {
        for (Files child : findChildren(file.getOwnerId(), file.getId())) {
            if (Byte.valueOf((byte) 1).equals(child.getStatus())) {
                softDeleteSubtree(child);
            }
        }

        file.setStatus((byte) 0);
        if (baseMapper.updateById(file) != 1) {
            throw new IllegalStateException("文件删除失败");
        }
        releaseFileReference(file);
    }

    private Files getRecycleBinFile(Long userId, Long fileId) {
        Files file = baseMapper.selectById(fileId);
        if (file == null || !Objects.equals(file.getOwnerId(), userId) || !Byte.valueOf((byte) 0).equals(file.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return file;
    }

    private List<Files> recycleBinSelectionRoots(Long userId, List<Long> fileIds) {
        Map<Long, Files> selectedFiles = new LinkedHashMap<>();
        for (Long fileId : fileIds) {
            selectedFiles.putIfAbsent(fileId, getRecycleBinFile(userId, fileId));
        }
        Set<Long> selectedIds = selectedFiles.keySet();
        return selectedFiles.values().stream()
                .filter(file -> !hasSelectedAncestor(file, selectedIds))
                .toList();
    }

    private boolean hasSelectedAncestor(Files file, Set<Long> selectedIds) {
        Long parentId = file.getParentId();
        while (parentId != null) {
            if (selectedIds.contains(parentId)) {
                return true;
            }
            Files parent = baseMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getOwnerId(), file.getOwnerId())) {
                return false;
            }
            parentId = parent.getParentId();
        }
        return false;
    }

    private void ensureParentIsAvailableForRestore(Long userId, Files file) {
        if (file.getParentId() == null) {
            return;
        }
        Files parent = baseMapper.selectById(file.getParentId());
        if (parent == null || !Objects.equals(parent.getOwnerId(), userId)
                || !Byte.valueOf((byte) 1).equals(parent.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "请先恢复父文件夹");
        }
    }

    private void restoreSubtree(Files file) {
        file.setStatus((byte) 1);
        if (baseMapper.updateById(file) != 1) {
            throw new IllegalStateException("文件恢复失败");
        }
        restoreFileReference(file);
        for (Files child : findChildren(file.getOwnerId(), file.getId())) {
            if (Byte.valueOf((byte) 0).equals(child.getStatus())) {
                restoreSubtree(child);
            }
        }
    }

    private void ensureSubtreeIsDeleted(Files file) {
        for (Files child : findChildren(file.getOwnerId(), file.getId())) {
            if (!Byte.valueOf((byte) 0).equals(child.getStatus())) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "文件夹包含未删除的内容，无法永久删除");
            }
            ensureSubtreeIsDeleted(child);
        }
    }

    private void permanentlyDeleteSubtree(Files file) {
        for (Files child : findChildren(file.getOwnerId(), file.getId())) {
            permanentlyDeleteSubtree(child);
        }
        if (baseMapper.deleteById(file.getId()) != 1) {
            throw new IllegalStateException("永久删除失败");
        }
        deleteOrphanedContent(file);
    }

    private void deleteOrphanedContent(Files file) {
        if (Boolean.TRUE.equals(file.getIsFolder()) || file.getContentId() == null) {
            return;
        }
        Long remainingReferences = baseMapper.selectCount(new LambdaQueryWrapper<Files>()
                .eq(Files::getContentId, file.getContentId()));
        if (remainingReferences != 0) {
            return;
        }
        FileContents content = fileContentsService.getById(file.getContentId());
        if (content == null || !fileContentsService.removeById(content.getId())) {
            return;
        }
        deleteContentObjectAfterCommit(content);
    }

    private void deleteContentObjectAfterCommit(FileContents content) {
        Runnable cleanup = () -> {
            try {
                MinIOUtil.ObjectLocation location = minIOUtil.parseStoragePath(content.getStoragePath(), FILES_BUCKET);
                minIOUtil.deleteObject(location.bucketName(), location.objectKey());
            } catch (Exception exception) {
                log.error("清理无引用文件对象失败: contentId={}", content.getId(), exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
            return;
        }
        cleanup.run();
    }

    private List<Files> findChildren(Long userId, Long parentId) {
        return baseMapper.selectList(new LambdaQueryWrapper<Files>()
                .eq(Files::getOwnerId, userId)
                .eq(Files::getParentId, parentId));
    }

    private void releaseFileReference(Files file) {
        if (Boolean.TRUE.equals(file.getIsFolder()) || file.getContentId() == null) {
            return;
        }
        storageBucketsService.releaseStorage(file.getBucketId(), file.getSize());
        FileContents content = fileContentsService.getById(file.getContentId());
        if (content != null) {
            content.setReferenceCount(Math.max(0, content.getReferenceCount() - 1));
            fileContentsService.updateById(content);
        }
    }

    private void restoreFileReference(Files file) {
        if (Boolean.TRUE.equals(file.getIsFolder()) || file.getContentId() == null) {
            return;
        }
        if (!storageBucketsService.reserveStorage(file.getBucketId(), file.getOwnerId(), file.getSize())) {
            throw new BusinessException(ResultCode.STORAGE_QUOTA_EXCEEDED);
        }
        FileContents content = fileContentsService.getById(file.getContentId());
        if (content != null) {
            content.setReferenceCount(content.getReferenceCount() + 1);
            fileContentsService.updateById(content);
        }
    }

    /**
     * 重命名文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Files renameFile(Long userId, Long fileId, String newName) {
        Files file = getFileInfo(userId, fileId);
        Assert.notNull(file, "文件不存在");

        file.setFilename(newName);
        file.setPath(buildPath(file.getParentId(), newName));
        baseMapper.updateById(file);
        return file;
    }

    /**
     * 移动文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Files moveFile(Long userId, Long fileId, Long targetParentId) {
        Files file = getFileInfo(userId, fileId);
        Assert.notNull(file, "文件不存在");

        file.setParentId(targetParentId);
        file.setPath(buildPath(targetParentId, file.getFilename()));
        baseMapper.updateById(file);
        return file;
    }

    /**
     * 搜索文件
     */
    @Override
    public List<Files> searchFiles(Long userId, String keyword, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Files> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Files::getOwnerId, userId)
                .like(Files::getFilename, keyword)
                .eq(Files::getStatus, 1)
                .orderByDesc(Files::getCreatedAt);

        int offset = (pageNum - 1) * pageSize;
        return baseMapper.selectList(wrapper.last("LIMIT " + offset + ", " + pageSize));
    }

    /**
     * 下载文件
     * 从 MinIO 读取文件内容并流式输出到 HttpServletResponse
     */
    @Override
    public void downloadFile(Long userId, Long fileId, HttpServletResponse response) {
        log.info("下载文件: userId={}, fileId={}", userId, fileId);
        streamFile(userId, fileId, null, false, response);
    }

    @Override
    public FilePreviewUrlResponse createPreviewUrl(Long userId, Long fileId) {
        Files file = getFileInfo(userId, fileId);
        if (file == null || Boolean.TRUE.equals(file.getIsFolder())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(PREVIEW_TICKET_PREFIX + ticket, userId + ":" + fileId,
                PREVIEW_TICKET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return FilePreviewUrlResponse.builder()
                .previewUrl("/api/files/preview/" + fileId + "?ticket=" + ticket)
                .expiresInSeconds((int) PREVIEW_TICKET_TIMEOUT_SECONDS)
                .build();
    }

    @Override
    public void previewFile(Long fileId, String ticket, String rangeHeader, HttpServletResponse response) {
        Long userId = resolvePreviewTicket(fileId, ticket);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        streamFile(userId, fileId, rangeHeader, true, response);
    }

    private Long resolvePreviewTicket(Long fileId, String ticket) {
        if (ticket == null || !ticket.matches("^[a-fA-F0-9-]{36}$")) {
            return null;
        }
        Object ticketValue = redisTemplate.opsForValue().get(PREVIEW_TICKET_PREFIX + ticket);
        if (ticketValue == null) {
            return null;
        }
        String[] ticketParts = ticketValue.toString().split(":", -1);
        if (ticketParts.length != 2) {
            return null;
        }
        try {
            Long ticketUserId = Long.valueOf(ticketParts[0]);
            return Objects.equals(Long.valueOf(ticketParts[1]), fileId) ? ticketUserId : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Streams an owned file as either an attachment or an inline range-capable preview. */
    private void streamFile(Long userId, Long fileId, String rangeHeader, boolean inline, HttpServletResponse response) {
        try {
            // 1. 获取文件元数据并验证所有权
            Files file = getFileInfo(userId, fileId);
            if (file == null || Boolean.TRUE.equals(file.getIsFolder())) {
                log.warn("文件不存在或不是普通文件: fileId={}", fileId);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // 2. 获取文件内容记录（含 MinIO 存储路径）
            FileContents content = fileContentsService.getById(file.getContentId());
            if (content == null || content.getStatus() != 1) {
                log.warn("文件内容记录不存在或已失效: contentId={}", file.getContentId());
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // 3. 从 MinIO 获取文件输入流
            MinIOUtil.ObjectLocation objectLocation = minIOUtil.parseStoragePath(content.getStoragePath(), FILES_BUCKET);
            log.debug("从 MinIO 读取文件: bucket={}, fileId={}", objectLocation.bucketName(), fileId);

            // 4. 设置响应头
            String filename = file.getFilename();
            response.setContentType(inline ? safeInlineContentType(content.getMimeType()) : safeContentType(content.getMimeType()));
            response.setHeader("Content-Disposition", (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(filename, StandardCharsets.UTF_8).build().toString());
            response.setHeader("Accept-Ranges", "bytes");

            ByteRange requestedRange = parseSingleRange(rangeHeader, content.getSize());
            if (requestedRange != null) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + requestedRange.start() + "-" + requestedRange.end()
                        + "/" + content.getSize());
                response.setHeader("Content-Length", String.valueOf(requestedRange.length()));
                try (InputStream inputStream = minIOUtil.getObjectRange(objectLocation.bucketName(), objectLocation.objectKey(),
                        requestedRange.start(), requestedRange.length())) {
                    StreamUtils.copy(inputStream, response.getOutputStream());
                }
            } else {
                response.setHeader("Content-Length", String.valueOf(content.getSize()));
                try (InputStream inputStream = minIOUtil.getObject(objectLocation.bucketName(), objectLocation.objectKey())) {
                    StreamUtils.copy(inputStream, response.getOutputStream());
                }
            }
            response.flushBuffer();

            log.info("文件{}成功: fileId={}, filename={}, size={}", inline ? "预览" : "下载", fileId, filename, content.getSize());

        } catch (IllegalArgumentException e) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            }
        } catch (Exception e) {
            if (isClientDisconnect(e)) {
                log.debug("客户端中断文件下载: fileId={}", fileId);
                return;
            }
            log.error("文件下载失败: fileId={}, error={}", fileId, e.getMessage(), e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private ByteRange parseSingleRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return null;
        }
        if (totalSize <= 0 || !rangeHeader.startsWith("bytes=") || rangeHeader.indexOf(',') >= 0) {
            throw new IllegalArgumentException("不支持的Range请求");
        }
        String[] boundaries = rangeHeader.substring("bytes=".length()).trim().split("-", -1);
        if (boundaries.length != 2) {
            throw new IllegalArgumentException("不支持的Range请求");
        }
        try {
            if (boundaries[0].isBlank()) {
                long suffixLength = Long.parseLong(boundaries[1]);
                if (suffixLength <= 0) {
                    throw new IllegalArgumentException("不支持的Range请求");
                }
                long length = Math.min(suffixLength, totalSize);
                return new ByteRange(totalSize - length, totalSize - 1);
            }
            long start = Long.parseLong(boundaries[0]);
            long end = boundaries[1].isBlank() ? totalSize - 1 : Math.min(Long.parseLong(boundaries[1]), totalSize - 1);
            if (start < 0 || start >= totalSize || end < start) {
                throw new IllegalArgumentException("不支持的Range请求");
            }
            return new ByteRange(start, end);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("不支持的Range请求", exception);
        }
    }

    private record ByteRange(long start, long end) {
        private long length() {
            return end - start + 1;
        }
    }

    private String safeContentType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return MediaType.parseMediaType(mimeType).toString();
        } catch (IllegalArgumentException exception) {
            log.warn("忽略非法文件MIME类型: mimeType={}", mimeType);
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    /** Avoid serving user-supplied active document types inline from this origin. */
    private String safeInlineContentType(String mimeType) {
        String contentType = safeContentType(mimeType);
        boolean isSafeImage = contentType.startsWith("image/") && !"image/svg+xml".equalsIgnoreCase(contentType);
        boolean isStreamableMedia = contentType.startsWith("video/") || contentType.startsWith("audio/");
        if (isSafeImage || isStreamableMedia || MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)) {
            return contentType;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private boolean isClientDisconnect(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            String normalized = message.toLowerCase(Locale.ROOT);
            if (normalized.contains("connection reset by peer") || normalized.contains("broken pipe")
                    || normalized.contains("clientabortexception")) {
                return true;
            }
        }
        return false;
    }

    // ==================== 辅助方法 ====================

    /**
     * 计算文件hash
     */
    private String calculateFileHash(InputStream inputStream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }
        byte[] hash = digest.digest();
        return bytesToHex(hash);
    }

    /**
     * Reads only a bounded range from the completed object to create the next
     * second-upload challenge. The file itself never passes through Spring.
     */
    private Object[] generateMultipartRandomPositionChecksum(String objectKey, long fileSize) {
        int length = (int) Math.min(fileSize, RANDOM_CHALLENGE_BLOCK_SIZE);
        long maxOffset = fileSize - length;
        long offset = maxOffset == 0 ? 0 : ThreadLocalRandom.current().nextLong(maxOffset + 1);
        try (InputStream inputStream = minIOUtil.getObjectRange(FILES_BUCKET, objectKey, offset, length)) {
            return new Object[]{offset, length, calculateFileHash(inputStream)};
        } catch (Exception e) {
            throw new RuntimeException("生成对象随机位置校验失败", e);
        }
    }

    /**
     * 生成随机位置校验参数及其hash值
     * 用于秒传时的双重校验，增强安全性
     * 
     * 策略：
     * - 随机选择一个位置，距离文件末尾至少256KB
     * - 从该位置读取256KB的数据块
     * - 计算该块的MD5 hash
     * - 返回位置、长度和hash三元组用于秒传校验
     * 
     * @param file 上传的文件对象
     * @param fileSize 文件总大小
     * @return 包含随机位置、长度和hash的对象数组 [offset, length, hash]
     */
    private Object[] generateRandomPositionChecksum(MultipartFile file, long fileSize) throws Exception {
        return generateRandomPositionChecksum(file.getInputStream(), fileSize);
    }

    /**
     * 生成随机位置校验参数及其hash值（InputStream版本）
     * 
     * @param inputStream 文件输入流
     * @param fileSize 文件总大小
     * @return 包含随机位置、长度和hash的对象数组 [offset, length, hash]
     */
    private Object[] generateRandomPositionChecksum(InputStream inputStream, long fileSize) throws Exception {
        // 定义随机块大小：256KB
        final int RANDOM_BLOCK_SIZE = 256 * 1024;
        
        // 如果文件小于512KB，直接使用第一个256KB
        if (fileSize <= RANDOM_BLOCK_SIZE) {
            long offset = 0;
            int length = (int) Math.min(fileSize, RANDOM_BLOCK_SIZE);
            String hash = calculateRandomPositionHash(inputStream, offset, length);
            log.info("文件较小，使用起始位置校验: offset={}, length={}, hash={}", offset, length, hash);
            return new Object[]{offset, length, hash};
        }
        
        // 文件足够大，在安全范围内随机选择位置
        // 安全范围：从0到 (fileSize - RANDOM_BLOCK_SIZE)
        long maxOffset = fileSize - RANDOM_BLOCK_SIZE;
        long randomOffset = (long) (Math.random() * maxOffset);
        
        // 确保对齐到1KB边界，便于后续操作
        randomOffset = (randomOffset / 1024) * 1024;
        
        String hash = calculateRandomPositionHash(inputStream, randomOffset, RANDOM_BLOCK_SIZE);
        
        log.info("生成随机位置校验: offset={}, length={}, totalSize={}, hash={}", 
                 randomOffset, RANDOM_BLOCK_SIZE, fileSize, hash);
        
        return new Object[]{randomOffset, RANDOM_BLOCK_SIZE, hash};
    }

    /**
     * 计算指定位置的数据块hash值
     * 
     * @param inputStream 文件输入流
     * @param offset 起始字节位置
     * @param length 字节长度
     * @return hash值
     */
    private String calculateRandomPositionHash(InputStream inputStream, long offset, int length) throws Exception {
        try {
            // 1. 跳过到指定位置
            long skipped = 0;
            while (skipped < offset) {
                long skip = offset - skipped;
                long actualSkip = inputStream.skip(skip);
                if (actualSkip == 0 && skipped < offset) {
                    throw new RuntimeException("无法跳过到指定位置: offset=" + offset + ", skipped=" + skipped);
                }
                skipped += actualSkip;
            }

            // 2. 读取指定长度的数据
            byte[] buffer = new byte[Math.min(8192, length)];
            MessageDigest digest = MessageDigest.getInstance("MD5");
            int bytesRead;
            int totalRead = 0;
            
            while (totalRead < length && (bytesRead = inputStream.read(buffer, 0, Math.min(buffer.length, length - totalRead))) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }

            if (totalRead < length) {
                log.warn("读取的字节数不足: offset={}, expectedLength={}, actualRead={}", offset, length, totalRead);
            }

            // 3. 返回hash
            byte[] hash = digest.digest();
            return bytesToHex(hash);

        } catch (Exception e) {
            log.error("计算随机位置hash失败: offset={}, length={}, error={}", offset, length, e.getMessage());
            throw new RuntimeException("计算随机位置hash失败: " + e.getMessage());
        }
    }

    /**
     * 验证秒传的随机位置校验
     * 在秒传前验证文件的随机位置数据，确保不是虚假的秒传请求
     * 
     * @param file 上传的文件对象
     * @param fileContents 存储的文件内容信息
     * @return true 验证通过，false 验证失败
     */
    private boolean verifySecondUploadRandomPosition(MultipartFile file, FileContents fileContents) throws Exception {
        if (fileContents.getRandomOffset() == null || fileContents.getRandomPositionHash() == null) {
            log.warn("文件记录不包含随机位置校验信息，跳过验证: contentId={}", fileContents.getId());
            return true; // 如果没有校验信息，允许秒传（向后兼容）
        }

        try {
            long offset = fileContents.getRandomOffset();
            int length = fileContents.getRandomLength() != null ? fileContents.getRandomLength() : 256 * 1024;
            String expectedHash = fileContents.getRandomPositionHash();

            // 计算实际的随机位置hash
            String actualHash = calculateRandomPositionHash(file.getInputStream(), offset, length);

            if (!actualHash.equals(expectedHash)) {
                log.error("秒传随机位置校验失败: 文件内容不匹配 contentId={}, offset={}, expectedHash={}, actualHash={}", 
                          fileContents.getId(), offset, expectedHash, actualHash);
                return false;
            }

            log.info("秒传随机位置校验通过: contentId={}, offset={}, length={}", 
                     fileContents.getId(), offset, length);
            return true;

        } catch (Exception e) {
            log.error("秒传验证随机位置异常: contentId={}, error={}", fileContents.getId(), e.getMessage(), e);
            throw new RuntimeException("秒传验证失败: " + e.getMessage());
        }
    }

    /**
     * 验证分片随机位置的内容
     * 用于防止数据被篡改或上传错误的分片
     *
     * @param inputStream 分片输入流
     * @param randomOffset 随机位置的起始字节位置
     * @param randomLength 随机位置的字节长度
     * @param expectedHash 期望的hash值
     * @param uploadId 上传ID（用于日志）
     * @param chunkIndex 分片索引（用于日志）
     */
    private void verifyRandomPosition(
            InputStream inputStream,
            Long randomOffset,
            Integer randomLength,
            String expectedHash,
            String uploadId,
            Integer chunkIndex) throws Exception {

        try {
            // 1. 跳过到指定位置
            long skipped = 0;
            while (skipped < randomOffset) {
                long skip = randomOffset - skipped;
                long actualSkip = inputStream.skip(skip);
                if (actualSkip == 0) {
                    throw new RuntimeException("无法跳过到指定位置，可能是分片损坏或过小");
                }
                skipped += actualSkip;
            }

            // 2. 读取指定长度的字节
            byte[] randomData = new byte[randomLength];
            int bytesRead = inputStream.read(randomData);
            if (bytesRead != randomLength) {
                log.warn("读取的字节数不足: uploadId={}, chunkIndex={}, offset={}, length={}, actualRead={}", 
                         uploadId, chunkIndex, randomOffset, randomLength, bytesRead);
                throw new RuntimeException("分片数据不足，读取的字节数: " + bytesRead + "，期望: " + randomLength);
            }

            // 3. 计算这部分的hash
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(randomData);
            String actualHash = bytesToHex(digest.digest());

            // 4. 验证hash
            if (!actualHash.equals(expectedHash)) {
                log.error("随机位置校验失败: uploadId={}, chunkIndex={}, offset={}, length={}, expectedHash={}, actualHash={}", 
                          uploadId, chunkIndex, randomOffset, randomLength, expectedHash, actualHash);
                throw new RuntimeException("随机位置校验失败: 数据可能被篡改，期望hash: " + expectedHash + "，实际hash: " + actualHash);
            }

            log.info("随机位置校验成功: uploadId={}, chunkIndex={}, offset={}, length={}", 
                     uploadId, chunkIndex, randomOffset, randomLength);

        } catch (Exception e) {
            log.error("验证随机位置异常: uploadId={}, chunkIndex={}, error={}", uploadId, chunkIndex, e.getMessage(), e);
            throw new RuntimeException("随机位置验证失败: " + e.getMessage());
        }
    }

    /**
     * 字节数组转16进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 生成对象名称
     */
    private String generateObjectName(String filename) {
        String uuid = UUID.randomUUID().toString();
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf(".")) : "";
        return uuid + extension;
    }

    /**
     * 生成分片对象名称
     */
    private String generateChunkObjectName(String uploadId, Integer chunkIndex) {
        return uploadId + "/" + chunkIndex;
    }

    /**
     * 合并分片文件
     * 从MinIO下载所有分片，按顺序合并，然后上传到最终位置
     */
    private void mergeChunks(String uploadId, Integer totalChunks, String mergedObjectName) throws Exception {
        log.info("开始合并分片: uploadId={}, totalChunks={}, mergedObjectName={}", uploadId, totalChunks, mergedObjectName);
        
        try {
            // 1. 从MinIO下载所有分片并按顺序读取
            List<InputStream> chunkStreams = new ArrayList<>();
            try {
                for (int i = 0; i < totalChunks; i++) {
                    String chunkObjectName = generateChunkObjectName(uploadId, i);
                    InputStream chunkStream = minIOUtil.getObject("chunks", chunkObjectName);
                    chunkStreams.add(chunkStream);
                    log.debug("成功获取分片流: {}", chunkObjectName);
                }
                
                // 2. 创建组合输入流
                SequenceInputStream sequenceInputStream = new SequenceInputStream(
                    Collections.enumeration(chunkStreams)
                );
                
                // 3. 上传组合后的文件到最终位置
                String uploadPath = minIOUtil.uploadStream(sequenceInputStream, "files", mergedObjectName);
                log.info("分片合并完成，已上传到: {}", uploadPath);
                
                // 4. 清理MinIO中的临时分片
                cleanupChunkFiles(uploadId, totalChunks);
                
            } finally {
                // 确保关闭所有流
                for (InputStream stream : chunkStreams) {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (IOException e) {
                            log.warn("关闭分片流出错: {}", e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("合并分片失败: uploadId={}, error={}", uploadId, e.getMessage(), e);
            // 清理已上传的部分分片
            cleanupChunkFiles(uploadId, totalChunks);
            throw new RuntimeException("合并分片失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理MinIO中的临时分片文件
     */
    private void cleanupChunkFiles(String uploadId, Integer totalChunks) {
        try {
            for (int i = 0; i < totalChunks; i++) {
                String chunkObjectName = generateChunkObjectName(uploadId, i);
                minIOUtil.deleteObject("chunks", chunkObjectName);
                log.debug("已删除临时分片: {}", chunkObjectName);
            }
            log.info("已清理所有临时分片文件: uploadId={}", uploadId);
        } catch (Exception e) {
            log.warn("清理临时分片文件失败: {}", e.getMessage());
            // 不影响主流程
        }
    }

    /**
     * 构建文件路径
     */
    private String buildPath(Long parentId, String filename) {
        if (parentId == null) {
            return "/" + filename;
        }
        Files parent = baseMapper.selectById(parentId);
        if (parent == null) {
            return "/" + filename;
        }
        return parent.getPath() + "/" + filename;
    }

    /**
     * 创建文件记录
     */
    private Files createFileRecord(
            Long userId,
            String filename,
            Long fileSize,
            Long contentId,
            Long parentId,
            Long bucketId,
            String mimeType,
            Boolean isFolder) {

        Files file = new Files();
        file.setOwnerId(userId);
        file.setFilename(filename);
        file.setContentId(contentId);
        file.setParentId(parentId);
        file.setBucketId(bucketId);
        file.setIsFolder(isFolder);
        file.setSize(fileSize);
        file.setStatus((byte) 1);
        file.setPath(buildPath(parentId, filename));
        file.setLastAccessedTime(LocalDateTime.now());

        if (!Boolean.TRUE.equals(isFolder) && !storageBucketsService.reserveStorage(bucketId, userId, fileSize)) {
            throw new BusinessException(ResultCode.STORAGE_QUOTA_EXCEEDED);
        }
        baseMapper.insert(file);
        return file;
    }
}
