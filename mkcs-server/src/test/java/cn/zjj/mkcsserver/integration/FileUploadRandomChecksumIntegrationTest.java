package cn.zjj.mkcsserver.integration;

import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.StorageBuckets;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import cn.zjj.mkcsserver.mapper.FileContentsMapper;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.mapper.StorageBucketsMapper;
import cn.zjj.mkcsserver.mapper.UsersMapper;
import cn.zjj.mkcsserver.service.FilesService;
import com.zjj.mkcscommon.utils.MinIOUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
class FileUploadRandomChecksumIntegrationTest {

    private static final String OBJECT_BUCKET = "files";

    @Autowired
    private FilesService filesService;

    @Autowired
    private UsersMapper usersMapper;

    @Autowired
    private StorageBucketsMapper storageBucketsMapper;

    @Autowired
    private FilesMapper filesMapper;

    @Autowired
    private FileContentsMapper fileContentsMapper;

    @Autowired
    private MinIOUtil minIOUtil;

    private Long userId;
    private Long bucketId;
    private final List<Long> fileIds = new ArrayList<>();
    private final List<Long> contentIds = new ArrayList<>();
    private final List<String> objectNames = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Users user = new Users();
        user.setUsername("checksum_" + suffix);
        user.setNickname("checksum-test");
        user.setStatus((byte) 1);
        user.setCreatedAt(LocalDateTime.now());
        usersMapper.insert(user);
        userId = user.getId();

        StorageBuckets bucket = new StorageBuckets();
        bucket.setOwnerId(userId);
        bucket.setBucketType((byte) 0);
        bucket.setName("checksum-" + suffix.substring(0, 20));
        bucket.setTotalStorage(1024L * 1024L * 1024L);
        bucket.setUsedStorage(0L);
        bucket.setStatus((byte) 1);
        bucket.setCreatedAt(LocalDateTime.now());
        storageBucketsMapper.insert(bucket);
        bucketId = bucket.getId();

        if (!minIOUtil.bucketExists(OBJECT_BUCKET)) {
            assertThat(minIOUtil.createBucket(OBJECT_BUCKET)).isTrue();
        }
    }

    @AfterEach
    void tearDown() {
        for (Long fileId : fileIds) {
            filesMapper.deleteById(fileId);
        }
        for (Long contentId : contentIds) {
            fileContentsMapper.deleteById(contentId);
        }
        for (String objectName : objectNames) {
            minIOUtil.deleteObject(OBJECT_BUCKET, objectName);
        }
        if (bucketId != null) {
            storageBucketsMapper.deleteById(bucketId);
        }
        if (userId != null) {
            usersMapper.deleteById(userId);
        }
    }

    @Test
    void storesRandomChecksumAcceptsMatchingDuplicateAndRejectsTamperedDuplicate() {
        byte[] original = new byte[1024];
        for (int index = 0; index < original.length; index++) {
            original[index] = (byte) (index % 251);
        }

        FileUploadResponse firstUpload = upload("original.bin", original);
        assertThat(firstUpload.getIsSecondUpload()).isFalse();

        Files firstFile = filesMapper.selectById(firstUpload.getFileId());
        FileContents firstContent = fileContentsMapper.selectById(firstFile.getContentId());
        track(firstFile, firstContent);
        assertThat(firstContent.getRandomOffset()).isZero();
        assertThat(firstContent.getRandomLength()).isEqualTo(original.length);
        assertThat(firstContent.getRandomPositionHash()).hasSize(32);

        FileUploadResponse duplicateUpload = upload("duplicate.bin", original);
        assertThat(duplicateUpload.getIsSecondUpload()).isTrue();
        Files duplicateFile = filesMapper.selectById(duplicateUpload.getFileId());
        fileIds.add(duplicateFile.getId());
        assertThat(duplicateFile.getContentId()).isEqualTo(firstContent.getId());
        assertThat(fileContentsMapper.selectById(firstContent.getId()).getReferenceCount()).isEqualTo(2);

        byte[] tampered = original.clone();
        tampered[tampered.length - 1] ^= 0x01;
        FileUploadResponse tamperedUpload = upload("tampered.bin", tampered);
        assertThat(tamperedUpload.getIsSecondUpload()).isFalse();
        Files tamperedFile = filesMapper.selectById(tamperedUpload.getFileId());
        FileContents tamperedContent = fileContentsMapper.selectById(tamperedFile.getContentId());
        track(tamperedFile, tamperedContent);
        assertThat(tamperedContent.getId()).isNotEqualTo(firstContent.getId());
    }

    private FileUploadResponse upload(String filename, byte[] content) {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", content);
        return filesService.uploadFile(userId, file, null, bucketId);
    }

    private void track(Files file, FileContents content) {
        fileIds.add(file.getId());
        contentIds.add(content.getId());
        objectNames.add(URI.create(content.getStoragePath()).getPath().substring(("/" + OBJECT_BUCKET + "/").length()));
    }
}
