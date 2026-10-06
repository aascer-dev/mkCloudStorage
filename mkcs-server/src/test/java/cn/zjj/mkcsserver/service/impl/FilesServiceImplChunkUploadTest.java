package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.vo.FilePreviewUrlResponse;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UploadTasksService;
import com.zjj.mkcscommon.utils.MinIOUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilesServiceImplChunkUploadTest {

    private static final String UPLOAD_KEY = "upload:task:upload-1";

    @Mock
    private MinIOUtil minIOUtil;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private FileContentsService fileContentsService;

    @Mock
    private UploadTasksService uploadTasksService;

    @Mock
    private MultipartUploadPersistenceService multipartUploadPersistenceService;

    @Mock
    private StorageBucketsService storageBucketsService;

    @Mock
    private FilesMapper filesMapper;

    private FilesServiceImpl filesService;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        filesService = new FilesServiceImpl(minIOUtil, redisTemplate, fileContentsService, uploadTasksService,
                multipartUploadPersistenceService, storageBucketsService);
        ReflectionTestUtils.setField(filesService, "baseMapper", filesMapper);
        lenient().when(redisTemplate.opsForHash()).thenReturn((HashOperations) hashOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(storageBucketsService.reserveStorage(any(), any(), anyLong())).thenReturn(true);
    }

    @Test
    void firstFileUploadCreatesSharedBucketBeforeWritingObject() {
        when(minIOUtil.createBucket("files")).thenReturn(true);
        when(fileContentsService.save(any(FileContents.class))).thenAnswer(invocation -> {
            ((FileContents) invocation.getArgument(0)).setId(8L);
            return true;
        });
        MockMultipartFile file = chunk();

        filesService.uploadFile(10L, file, null, 9L);

        var storageOrder = inOrder(minIOUtil);
        storageOrder.verify(minIOUtil).bucketExists("files");
        storageOrder.verify(minIOUtil).createBucket("files");
        storageOrder.verify(minIOUtil).upload(eq(file), eq("files"), any(String.class));
    }

    @Test
    void bucketCreationFailureStopsFileUploadBeforeWritingObjectOrMetadata() {
        assertThatThrownBy(() -> filesService.uploadFile(10L, chunk(), null, 9L))
                .hasMessageContaining("文件存储桶不可用: files");

        verify(minIOUtil, never()).upload(any(), any(), any());
        verify(fileContentsService, never()).save(any(FileContents.class));
        verifyNoInteractions(filesMapper);
    }

    @Test
    void chunkInitializationFailsBeforeSavingTaskWhenStorageIsUnavailable() {
        assertThatThrownBy(() -> filesService.initChunkUpload(10L, "file.bin", 3L, "hash", 1, null, 9L, null))
                .hasMessageContaining("文件存储桶不可用: chunks");

        verify(minIOUtil).createBucket("chunks");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void firstChunkUploadCreatesSharedBucketBeforeWritingChunkAndProgress() {
        when(hashOperations.entries(UPLOAD_KEY)).thenReturn(uploadTask(10L, 1, new HashSet<>()));
        when(minIOUtil.createBucket("chunks")).thenReturn(true);
        MockMultipartFile file = chunk();

        ChunkUploadResponse response = filesService.uploadChunk(10L, "upload-1", 0, file, null, null, null, null);

        assertThat(response.getIsComplete()).isTrue();
        var storageOrder = inOrder(minIOUtil, hashOperations);
        storageOrder.verify(minIOUtil).createBucket("chunks");
        storageOrder.verify(minIOUtil).upload(file, "chunks", "upload-1/0");
        storageOrder.verify(hashOperations).put(UPLOAD_KEY, "uploadedChunks", Set.of(0));
    }

    @Test
    void uploadChunkRejectsMissingTaskWithoutWritingToStorage() {
        when(hashOperations.entries(UPLOAD_KEY)).thenReturn(Map.of());

        assertThatThrownBy(() -> filesService.uploadChunk(10L, "upload-1", 0, chunk(), null, null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("上传任务不存在或已过期");

        verifyNoInteractions(minIOUtil);
        verify(hashOperations, never()).put(any(), any(), any());
    }

    @Test
    void uploadChunkRejectsAnotherUsersTaskWithoutWritingToStorage() {
        when(hashOperations.entries(UPLOAD_KEY)).thenReturn(uploadTask(20L, 2, Set.of()));

        assertThatThrownBy(() -> filesService.uploadChunk(10L, "upload-1", 0, chunk(), null, null, null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权限上传此文件");

        verifyNoInteractions(minIOUtil);
        verify(hashOperations, never()).put(any(), any(), any());
    }

    @Test
    void duplicateChunkSkipsObjectStorageAndKeepsProgress() {
        when(hashOperations.entries(UPLOAD_KEY)).thenReturn(uploadTask(10L, 3, Set.of(1)));
        when(redisTemplate.expire(eq(UPLOAD_KEY), eq(86400L), any())).thenReturn(true);

        ChunkUploadResponse response = filesService.uploadChunk(10L, "upload-1", 1, chunk(), null, null, null, null);

        assertThat(response.getUploadedChunks()).containsExactly(1);
        assertThat(response.getProgress()).isEqualTo(33);
        assertThat(response.getIsComplete()).isFalse();
        verifyNoInteractions(minIOUtil);
        verify(hashOperations).put(eq(UPLOAD_KEY), eq("uploadedChunks"), eq(Set.of(1)));
    }

    @Test
    void completeChunkUploadRejectsMissingChunkBeforeMerging() {
        Map<Object, Object> task = uploadTask(10L, 3, Set.of(0, 2));
        task.put("filename", "archive.zip");
        task.put("fileSize", 30L);
        task.put("mimeType", "application/zip");
        task.put("fileHash", "hash");
        when(hashOperations.entries(UPLOAD_KEY)).thenReturn(task);

        assertThatThrownBy(() -> filesService.completeChunkUpload(10L, "upload-1", "hash"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("分片上传不完整");

        verifyNoInteractions(minIOUtil, fileContentsService);
    }

    @Test
    void cancelChunkUploadDeletesOnlyRecordedChunksAndTaskState() {
        when(hashOperations.entries(UPLOAD_KEY)).thenReturn(uploadTask(10L, 3, Set.of(0, 2)));

        filesService.cancelChunkUpload(10L, "upload-1");

        verify(minIOUtil).deleteObject("chunks", "upload-1/0");
        verify(minIOUtil).deleteObject("chunks", "upload-1/2");
        verify(redisTemplate).delete(UPLOAD_KEY);
    }

    @Test
    void downloadFileDoesNotReadObjectForAnotherUsersFile() {
        Files file = new Files();
        file.setOwnerId(20L);
        file.setStatus((byte) 1);
        file.setIsFolder(false);
        when(filesMapper.selectById(7L)).thenReturn(file);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filesService.downloadFile(10L, 7L, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(minIOUtil, fileContentsService);
    }

    @Test
    void downloadFileUsesParsedHistoricalObjectLocation() {
        Files file = new Files();
        file.setOwnerId(10L);
        file.setStatus((byte) 1);
        file.setIsFolder(false);
        file.setFilename("image.png");
        file.setContentId(8L);
        FileContents content = new FileContents();
        content.setStatus((byte) 1);
        content.setSize(3L);
        content.setMimeType("image/png");
        content.setStoragePath("http://127.0.0.1:19000/files/legacy-object.png");
        when(filesMapper.selectById(7L)).thenReturn(file);
        when(fileContentsService.getById(8L)).thenReturn(content);
        MinIOUtil.ObjectLocation location = new MinIOUtil.ObjectLocation("files", "legacy-object.png");
        when(minIOUtil.parseStoragePath(content.getStoragePath(), "files")).thenReturn(location);
        when(minIOUtil.getObject("files", "legacy-object.png")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filesService.downloadFile(10L, 7L, response);

        verify(minIOUtil).getObject("files", "legacy-object.png");
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3);
        assertThat(response.getContentType()).isEqualTo("image/png");
    }

    @Test
    void previewStreamsRequestedRangeForAValidShortLivedTicket() {
        Files file = new Files();
        file.setOwnerId(10L);
        file.setStatus((byte) 1);
        file.setIsFolder(false);
        file.setFilename("clip.mp4");
        file.setContentId(8L);
        FileContents content = new FileContents();
        content.setStatus((byte) 1);
        content.setSize(10L);
        content.setMimeType("video/mp4");
        content.setStoragePath("files/clip.mp4");
        when(valueOperations.get("file:preview-ticket:123e4567-e89b-12d3-a456-426614174000")).thenReturn("10:7");
        when(filesMapper.selectById(7L)).thenReturn(file);
        when(fileContentsService.getById(8L)).thenReturn(content);
        when(minIOUtil.parseStoragePath(content.getStoragePath(), "files"))
                .thenReturn(new MinIOUtil.ObjectLocation("files", "clip.mp4"));
        when(minIOUtil.getObjectRange("files", "clip.mp4", 2L, 3L))
                .thenReturn(new ByteArrayInputStream(new byte[]{3, 4, 5}));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filesService.previewFile(7L, "123e4567-e89b-12d3-a456-426614174000", "bytes=2-4", response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_PARTIAL_CONTENT);
        assertThat(response.getHeader("Content-Range")).isEqualTo("bytes 2-4/10");
        assertThat(response.getHeader("Accept-Ranges")).isEqualTo("bytes");
        assertThat(response.getHeader("Content-Disposition")).contains("inline");
        assertThat(response.getContentAsByteArray()).containsExactly(3, 4, 5);
    }

    @Test
    void previewUrlUsesAShortLivedOpaqueTicketInsteadOfTheLoginToken() {
        Files file = new Files();
        file.setOwnerId(10L);
        file.setStatus((byte) 1);
        file.setIsFolder(false);
        when(filesMapper.selectById(7L)).thenReturn(file);

        FilePreviewUrlResponse response = filesService.createPreviewUrl(10L, 7L);

        assertThat(response.getPreviewUrl()).startsWith("/api/files/preview/7?ticket=");
        assertThat(response.getExpiresInSeconds()).isEqualTo(300);
        verify(valueOperations).set(startsWith("file:preview-ticket:"), eq("10:7"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void deleteFileReleasesTheLogicalFileCapacity() {
        Files file = new Files();
        file.setOwnerId(10L);
        file.setStatus((byte) 1);
        file.setIsFolder(false);
        file.setContentId(8L);
        file.setBucketId(9L);
        file.setSize(5L);
        FileContents content = new FileContents();
        content.setReferenceCount(1);
        when(filesMapper.selectById(7L)).thenReturn(file);
        when(filesMapper.updateById(file)).thenReturn(1);
        when(fileContentsService.getById(8L)).thenReturn(content);

        filesService.deleteFile(10L, 7L);

        verify(storageBucketsService).releaseStorage(9L, 5L);
        assertThat(content.getReferenceCount()).isZero();
    }

    private MockMultipartFile chunk() {
        return new MockMultipartFile("chunk", "part-0", "application/octet-stream", new byte[]{1, 2, 3});
    }

    private Map<Object, Object> uploadTask(Long userId, int totalChunks, Set<Integer> uploadedChunks) {
        Map<Object, Object> task = new HashMap<>();
        task.put("userId", userId);
        task.put("totalChunks", totalChunks);
        task.put("uploadedChunks", uploadedChunks);
        return task;
    }
}
