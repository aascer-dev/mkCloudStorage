package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.FilesService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.utils.MinIOUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    // Redis key前缀
    private static final String UPLOAD_TASK_PREFIX = "upload:task:";
    private static final String CHUNK_PREFIX = "upload:chunk:";
    // 上传时间：24小时
    private static final long UPLOAD_TASK_TIMEOUT = 24 * 60 * 60;

    /**
     * 检查文件是否存在（秒传检查）
     */
    @Override
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
                String storagePath;
                try {
                    storagePath = minIOUtil.upload(file, "files", objectName);
                    log.info("文件上传到MinIO成功: filename={}, storagePath={}", filename, storagePath);
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
                fileContent.setStoragePath(storagePath);
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
            log.error("文件上传失败: filename={}, userId={}, error={}", filename, userId, e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 初始化分片上传
     * 检查秒传、生成上传ID、记录上传任务信息
     */
    @Override
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
            String mergedObjectName = generateObjectName(filename);
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
                .eq(Files::getParentId, folderId)
                .eq(Files::getStatus, 1)
                .orderByDesc(Files::getIsFolder)
                .orderByDesc(Files::getCreatedAt);

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

        // 软删除
        file.setStatus((byte) 0);
        baseMapper.updateById(file);

        // 如果是文件，减少内容引用计数
        if (!file.getIsFolder() && file.getContentId() != null) {
            FileContents content = fileContentsService.getById(file.getContentId());
            if (content != null) {
                content.setReferenceCount(Math.max(0, content.getReferenceCount() - 1));
                fileContentsService.updateById(content);
            }
        }
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

        try {
            // 1. 获取文件元数据并验证所有权
            Files file = getFileInfo(userId, fileId);
            if (file == null || file.getIsFolder()) {
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
            String storagePath = content.getStoragePath();
            // storagePath 格式为 "files/<objectName>"，需要拆分为 bucket 和 objectName
            String bucket = storagePath.substring(0, storagePath.indexOf('/'));
            String objectName = storagePath.substring(storagePath.indexOf('/') + 1);

            log.debug("从 MinIO 读取文件: bucket={}, objectName={}", bucket, objectName);
            InputStream inputStream = minIOUtil.getObject(bucket, objectName);

            // 4. 设置响应头
            String filename = file.getFilename();
            // 处理文件名编码（支持中文）
            String encodedFilename = new String(filename.getBytes("UTF-8"), "ISO-8859-1");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");
            response.setHeader("Content-Length", String.valueOf(content.getSize()));

            // 5. 流式输出到响应
            StreamUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();

            log.info("文件下载成功: fileId={}, filename={}, size={}", fileId, filename, content.getSize());

        } catch (Exception e) {
            log.error("文件下载失败: fileId={}, error={}", fileId, e.getMessage(), e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
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

        baseMapper.insert(file);
        return file;
    }
}
