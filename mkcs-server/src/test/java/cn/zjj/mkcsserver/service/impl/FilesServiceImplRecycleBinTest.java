package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.UploadTasks;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UploadTasksService;
import com.zjj.mkcscommon.utils.MinIOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilesServiceImplRecycleBinTest {

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
    }

    @Test
    void restoreReactivatesTheDeletedFileAndRestoresItsQuotaAndReference() {
        Files file = new Files();
        file.setId(7L);
        file.setOwnerId(10L);
        file.setBucketId(2L);
        file.setContentId(3L);
        file.setIsFolder(false);
        file.setSize(1024L);
        file.setStatus((byte) 0);
        FileContents content = new FileContents();
        content.setReferenceCount(2);
        when(filesMapper.selectById(7L)).thenReturn(file);
        when(filesMapper.updateById(any(Files.class))).thenReturn(1);
        when(filesMapper.selectList(any())).thenReturn(List.of());
        when(storageBucketsService.reserveStorage(2L, 10L, 1024L)).thenReturn(true);
        when(fileContentsService.getById(3L)).thenReturn(content);

        filesService.restoreFiles(10L, List.of(7L));

        ArgumentCaptor<Files> fileCaptor = ArgumentCaptor.forClass(Files.class);
        verify(filesMapper).updateById((Files) fileCaptor.capture());
        assertThat(fileCaptor.getValue().getStatus()).isEqualTo((byte) 1);
        verify(storageBucketsService).reserveStorage(2L, 10L, 1024L);
        assertThat(content.getReferenceCount()).isEqualTo(3);
        verify(fileContentsService).updateById(content);
    }

    @Test
    void permanentDeleteRemovesTheObjectOnlyAfterTheLastMetadataReferenceIsGone() {
        Files file = new Files();
        file.setId(7L);
        file.setOwnerId(10L);
        file.setBucketId(2L);
        file.setContentId(3L);
        file.setIsFolder(false);
        file.setStatus((byte) 0);
        FileContents content = new FileContents();
        content.setId(3L);
        content.setStoragePath("files/contents/object-3");
        when(filesMapper.selectById(7L)).thenReturn(file);
        when(filesMapper.selectList(any())).thenReturn(List.of());
        when(filesMapper.deleteById(7L)).thenReturn(1);
        when(filesMapper.selectCount(any())).thenReturn(0L);
        when(fileContentsService.getById(3L)).thenReturn(content);
        when(fileContentsService.removeById(3L)).thenReturn(true);
        when(minIOUtil.parseStoragePath("files/contents/object-3", "files"))
                .thenReturn(new MinIOUtil.ObjectLocation("files", "contents/object-3"));

        filesService.permanentlyDeleteFiles(10L, List.of(7L));

        verify(fileContentsService).removeById(3L);
        verify(minIOUtil).deleteObject("files", "contents/object-3");
    }
}
