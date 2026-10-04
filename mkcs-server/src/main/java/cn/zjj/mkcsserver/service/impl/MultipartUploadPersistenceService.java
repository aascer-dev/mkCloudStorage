package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.UploadTasks;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UploadTasksService;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Persists metadata after MinIO has completed an upload; no object storage calls occur here. */
@Service
@RequiredArgsConstructor
class MultipartUploadPersistenceService {

    private static final byte UPLOAD_STATUS_COMPLETED = 3;

    private final FileContentsService fileContentsService;
    private final FilesMapper filesMapper;
    private final UploadTasksService uploadTasksService;
    private final StorageBucketsService storageBucketsService;

    @Transactional(rollbackFor = Exception.class)
    public FileUploadResponse persist(UploadTasks task, Long randomOffset, Integer randomLength, String randomPositionHash) {
        FileContents existingContent = fileContentsService.getByContentHash(task.getFileHash());
        boolean duplicate = existingContent != null && existingContent.getStatus() == 1;
        if (duplicate) {
            throw new IllegalStateException("上传期间检测到相同内容，请重新初始化后完成秒传校验");
        }
        Long contentId;
        FileContents content = new FileContents();
        content.setContentHash(task.getFileHash());
        content.setSize(task.getTotalSize());
        content.setStoragePath("files/" + task.getObjectKey());
        content.setMimeType(task.getMimeType());
        content.setStatus((byte) 1);
        content.setReferenceCount(1);
        content.setRandomOffset(randomOffset);
        content.setRandomLength(randomLength);
        content.setRandomPositionHash(randomPositionHash);
        fileContentsService.save(content);
        contentId = content.getId();

        Files file = createFileRecord(task, contentId);
        task.setStatus(UPLOAD_STATUS_COMPLETED);
        task.setFinalFileId(file.getId());
        task.setUploadedSize(task.getTotalSize());
        task.setUploadedChunks(task.getTotalChunks());
        task.setErrorMessage(null);
        uploadTasksService.updateById(task);

        return FileUploadResponse.builder().fileId(file.getId()).filename(file.getFilename()).fileSize(file.getSize())
                .isSecondUpload(false).status("success").message("文件上传完成").build();
    }

    private Files createFileRecord(UploadTasks task, Long contentId) {
        Files file = new Files();
        file.setOwnerId(task.getUserId());
        file.setFilename(task.getFilename());
        file.setContentId(contentId);
        file.setParentId(task.getParentId());
        file.setBucketId(task.getBucketId());
        file.setIsFolder(false);
        file.setSize(task.getTotalSize());
        file.setStatus((byte) 1);
        file.setPath(buildPath(task.getParentId(), task.getFilename()));
        file.setLastAccessedTime(LocalDateTime.now());
        if (!storageBucketsService.reserveStorage(task.getBucketId(), task.getUserId(), task.getTotalSize())) {
            throw new BusinessException(ResultCode.STORAGE_QUOTA_EXCEEDED);
        }
        filesMapper.insert(file);
        return file;
    }

    private String buildPath(Long parentId, String filename) {
        if (parentId == null) {
            return "/" + filename;
        }
        Files parent = filesMapper.selectById(parentId);
        return parent == null ? "/" + filename : parent.getPath() + "/" + filename;
    }
}
