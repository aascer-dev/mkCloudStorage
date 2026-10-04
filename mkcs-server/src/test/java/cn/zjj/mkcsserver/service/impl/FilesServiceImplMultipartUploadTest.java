package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.dto.MultipartUploadCompleteRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadInitRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadPartRequest;
import cn.zjj.mkcsmodel.dto.MultipartSecondUploadVerifyRequest;
import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.UploadTasks;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadInitResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadStatusResponse;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UploadTasksService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zjj.mkcscommon.utils.MinIOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.model.Part;

import java.time.LocalDateTime;
import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FilesServiceImplMultipartUploadTest {
    @Mock private MinIOUtil minIOUtil;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private FileContentsService fileContentsService;
    @Mock private UploadTasksService uploadTasksService;
    @Mock private MultipartUploadPersistenceService multipartUploadPersistenceService;
    @Mock private StorageBucketsService storageBucketsService;
    @Mock private FilesMapper filesMapper;
    private FilesServiceImpl filesService;

    @BeforeEach
    void setUp() {
        filesService = new FilesServiceImpl(minIOUtil, redisTemplate, fileContentsService, uploadTasksService,
                multipartUploadPersistenceService, storageBucketsService);
        ReflectionTestUtils.setField(filesService, "baseMapper", filesMapper);
        lenient().when(storageBucketsService.reserveStorage(any(), any(), anyLong())).thenReturn(true);
    }

    @Test
    void statusUsesMinioPartsAsResumeSourceOfTruth() {
        when(uploadTasksService.getById(7L)).thenReturn(task());
        when(minIOUtil.listMultipartUploadParts("files", "contents/object", "minio-id"))
                .thenReturn(List.of(Part.builder().partNumber(1).eTag("etag-1").size(16L).build()));

        MultipartUploadStatusResponse response = filesService.getMultipartUploadStatus(10L, 7L);

        assertThat(response.getUploadedParts()).singleElement().satisfies(part -> {
            assertThat(part.getPartNumber()).isEqualTo(1);
            assertThat(part.getEtag()).isEqualTo("etag-1");
        });
    }

    @Test
    void completionRejectsClientEtagDifferentFromMinio() {
        when(uploadTasksService.getById(7L)).thenReturn(task());
        when(minIOUtil.listMultipartUploadParts("files", "contents/object", "minio-id"))
                .thenReturn(List.of(Part.builder().partNumber(1).eTag("actual-etag").size(16L).build()));
        MultipartUploadPartRequest part = new MultipartUploadPartRequest();
        part.setPartNumber(1); part.setEtag("forged-etag");
        MultipartUploadCompleteRequest request = new MultipartUploadCompleteRequest();
        request.setUploadId(7L); request.setParts(List.of(part));

        assertThatThrownBy(() -> filesService.completeMultipartUpload(10L, request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ETag");
        verify(minIOUtil, never()).completeMultipartUpload(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void cancelAbortsOwnedMultipartUpload() {
        UploadTasks task = task();
        when(uploadTasksService.getById(7L)).thenReturn(task);

        filesService.cancelMultipartUpload(10L, 7L);

        verify(minIOUtil).abortMultipartUpload("files", "contents/object", "minio-id");
        assertThat(task.getStatus()).isEqualTo((byte) 5);
        verify(uploadTasksService).updateById(task);
    }

    @Test
    void persistenceFailureCleansCompletedObjectAndMarksTaskFailed() {
        UploadTasks task = task();
        when(uploadTasksService.getById(7L)).thenReturn(task);
        when(minIOUtil.listMultipartUploadParts("files", "contents/object", "minio-id"))
                .thenReturn(List.of(Part.builder().partNumber(1).eTag("etag-1").size(16L).build()));
        when(minIOUtil.getObjectSize("files", "contents/object")).thenReturn(16L);
        when(minIOUtil.getObjectRange("files", "contents/object", 0L, 16))
                .thenReturn(new ByteArrayInputStream(new byte[16]));
        doThrow(new RuntimeException("database unavailable")).when(multipartUploadPersistenceService)
                .persist(eq(task), eq(0L), eq(16), anyString());
        MultipartUploadPartRequest part = new MultipartUploadPartRequest();
        part.setPartNumber(1); part.setEtag("etag-1");
        MultipartUploadCompleteRequest request = new MultipartUploadCompleteRequest();
        request.setUploadId(7L); request.setParts(List.of(part));

        assertThatThrownBy(() -> filesService.completeMultipartUpload(10L, request))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("database unavailable");

        verify(minIOUtil).deleteObject("files", "contents/object");
        assertThat(task.getStatus()).isEqualTo((byte) 4);
        verify(uploadTasksService).updateById(task);
    }

    @Test
    void completionPersistsRandomRangeChecksumForFutureSecondUploads() {
        UploadTasks task = task();
        when(uploadTasksService.getById(7L)).thenReturn(task);
        when(minIOUtil.listMultipartUploadParts("files", "contents/object", "minio-id"))
                .thenReturn(List.of(Part.builder().partNumber(1).eTag("etag-1").size(16L).build()));
        when(minIOUtil.getObjectSize("files", "contents/object")).thenReturn(16L);
        when(minIOUtil.getObjectRange("files", "contents/object", 0L, 16))
                .thenReturn(new ByteArrayInputStream(new byte[16]));
        when(multipartUploadPersistenceService.persist(eq(task), eq(0L), eq(16), anyString()))
                .thenReturn(FileUploadResponse.builder().fileId(11L).isSecondUpload(false).build());
        MultipartUploadPartRequest part = new MultipartUploadPartRequest();
        part.setPartNumber(1); part.setEtag("etag-1");
        MultipartUploadCompleteRequest request = new MultipartUploadCompleteRequest();
        request.setUploadId(7L); request.setParts(List.of(part));

        filesService.completeMultipartUpload(10L, request);

        verify(minIOUtil).getObjectRange("files", "contents/object", 0L, 16);
        verify(multipartUploadPersistenceService).persist(task, 0L, 16, "4ae71336e44bf9bf79d2752e234818a5");
    }

    @Test
    void existingContentCreatesChallengeInsteadOfInstantUpload() {
        MultipartUploadInitRequest request = initRequest();
        FileContents content = challengeContent();
        when(uploadTasksService.list(any(Wrapper.class))).thenReturn(List.of());
        when(fileContentsService.getByContentHash(request.getFileHash())).thenReturn(content);
        when(uploadTasksService.save(any())).thenAnswer(invocation -> {
            ((UploadTasks) invocation.getArgument(0)).setId(9L);
            return true;
        });

        MultipartUploadInitResponse response = filesService.initMultipartUpload(10L, request);

        assertThat(response.getInstantUpload()).isFalse();
        assertThat(response.getSecondUploadChallenge()).isTrue();
        assertThat(response.getUploadId()).isEqualTo(9L);
        assertThat(response.getChallengeOffset()).isEqualTo(2L);
        assertThat(response.getChallengeLength()).isEqualTo(3);
        verifyNoInteractions(minIOUtil);
    }

    @Test
    void activeChallengeIsReturnedWhenInitializationIsRetried() {
        MultipartUploadInitRequest request = initRequest();
        UploadTasks task = task();
        task.setMinioUploadId(null);
        task.setFileHash(request.getFileHash());
        when(uploadTasksService.list(any(Wrapper.class))).thenReturn(List.of(task));
        when(fileContentsService.getByContentHash(task.getFileHash())).thenReturn(challengeContent());

        MultipartUploadInitResponse response = filesService.initMultipartUpload(10L, request);

        assertThat(response.getUploadId()).isEqualTo(7L);
        assertThat(response.getSecondUploadChallenge()).isTrue();
        verify(uploadTasksService, never()).save(any());
        verifyNoInteractions(minIOUtil);
    }

    @Test
    void challengeVerificationCreatesReferenceOnlyWhenHashMatches() {
        UploadTasks task = task();
        task.setMinioUploadId(null);
        task.setFileHash("a".repeat(64));
        task.setFilename("duplicate.bin");
        FileContents content = challengeContent();
        Files createdFile = new Files();
        createdFile.setId(88L);
        when(uploadTasksService.getById(7L)).thenReturn(task);
        when(fileContentsService.getByContentHash(task.getFileHash())).thenReturn(content);
        when(filesMapper.insert(any(Files.class))).thenAnswer(invocation -> {
            Files file = invocation.getArgument(0);
            file.setId(createdFile.getId());
            return 1;
        });
        MultipartSecondUploadVerifyRequest request = new MultipartSecondUploadVerifyRequest();
        request.setUploadId(7L);
        request.setChallengeHash("900150983cd24fb0d6963f7d28e17f72");

        FileUploadResponse response = filesService.verifyMultipartSecondUpload(10L, request);

        assertThat(response.getIsSecondUpload()).isTrue();
        assertThat(response.getFileId()).isEqualTo(88L);
        assertThat(content.getReferenceCount()).isEqualTo(2);
        assertThat(task.getStatus()).isEqualTo((byte) 3);
        verify(storageBucketsService).reserveStorage(9L, 10L, 16L);
        verifyNoInteractions(minIOUtil);
    }

    @Test
    void challengeVerificationRejectsSecondUploadWhenBucketQuotaIsExhausted() {
        UploadTasks task = task();
        task.setMinioUploadId(null);
        task.setFileHash("a".repeat(64));
        task.setFilename("duplicate.bin");
        FileContents content = challengeContent();
        when(uploadTasksService.getById(7L)).thenReturn(task);
        when(fileContentsService.getByContentHash(task.getFileHash())).thenReturn(content);
        when(storageBucketsService.reserveStorage(9L, 10L, 16L)).thenReturn(false);
        MultipartSecondUploadVerifyRequest request = new MultipartSecondUploadVerifyRequest();
        request.setUploadId(7L);
        request.setChallengeHash("900150983cd24fb0d6963f7d28e17f72");

        assertThatThrownBy(() -> filesService.verifyMultipartSecondUpload(10L, request))
                .hasMessageContaining("存储空间不足");

        verify(filesMapper, never()).insert(any(Files.class));
        verifyNoInteractions(minIOUtil);
    }

    @Test
    void challengeVerificationRejectsMismatchedHashBeforeCreatingReference() {
        UploadTasks task = task();
        task.setMinioUploadId(null);
        task.setFileHash("a".repeat(64));
        when(uploadTasksService.getById(7L)).thenReturn(task);
        when(fileContentsService.getByContentHash(task.getFileHash())).thenReturn(challengeContent());
        MultipartSecondUploadVerifyRequest request = new MultipartSecondUploadVerifyRequest();
        request.setUploadId(7L);
        request.setChallengeHash("0".repeat(32));

        assertThatThrownBy(() -> filesService.verifyMultipartSecondUpload(10L, request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("随机切片");

        verify(fileContentsService, never()).updateById(any());
        verifyNoInteractions(filesMapper, minIOUtil);
    }

    private MultipartUploadInitRequest initRequest() {
        MultipartUploadInitRequest request = new MultipartUploadInitRequest();
        request.setFilename("duplicate.bin");
        request.setFileSize(16L);
        request.setFileHash("a".repeat(64));
        request.setMimeType("application/octet-stream");
        return request;
    }

    private FileContents challengeContent() {
        FileContents content = new FileContents();
        content.setId(5L);
        content.setStatus((byte) 1);
        content.setSize(16L);
        content.setReferenceCount(1);
        content.setRandomOffset(2L);
        content.setRandomLength(3);
        content.setRandomPositionHash("900150983cd24fb0d6963f7d28e17f72");
        return content;
    }

    private UploadTasks task() {
        UploadTasks task = new UploadTasks();
        task.setId(7L); task.setUserId(10L); task.setStatus((byte) 1);
        task.setBucketId(9L);
        task.setObjectKey("contents/object"); task.setMinioUploadId("minio-id");
        task.setChunkSize(16); task.setTotalChunks(1); task.setTotalSize(16L);
        task.setExpireTime(LocalDateTime.now().plusHours(1));
        return task;
    }
}
