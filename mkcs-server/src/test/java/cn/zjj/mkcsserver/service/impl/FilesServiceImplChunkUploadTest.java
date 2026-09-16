package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import com.zjj.mkcscommon.utils.MinIOUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    private FileContentsService fileContentsService;

    @Mock
    private FilesMapper filesMapper;

    private FilesServiceImpl filesService;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        filesService = new FilesServiceImpl(minIOUtil, redisTemplate, fileContentsService);
        ReflectionTestUtils.setField(filesService, "baseMapper", filesMapper);
        lenient().when(redisTemplate.opsForHash()).thenReturn((HashOperations) hashOperations);
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
