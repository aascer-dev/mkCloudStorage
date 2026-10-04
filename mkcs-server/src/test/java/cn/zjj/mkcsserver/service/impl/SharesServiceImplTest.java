package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.dto.CreateShareRequest;
import cn.zjj.mkcsmodel.dto.PublicShareAccessRequest;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.entity.Shares;
import cn.zjj.mkcsmodel.vo.ShareResponse;
import cn.zjj.mkcsserver.mapper.SharesMapper;
import cn.zjj.mkcsserver.service.CryptoService;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.FilesService;
import com.zjj.mkcscommon.utils.MinIOUtil;
import com.zjj.mkcscommon.result.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharesServiceImplTest {

    @Mock
    private SharesMapper sharesMapper;

    @Mock
    private FilesService filesService;

    @Mock
    private CryptoService cryptoService;

    @Mock
    private FileContentsService fileContentsService;

    @Mock
    private MinIOUtil minIOUtil;

    private SharesServiceImpl sharesService;

    @BeforeEach
    void setUp() {
        sharesService = new SharesServiceImpl(filesService, cryptoService, fileContentsService, minIOUtil);
        ReflectionTestUtils.setField(sharesService, "baseMapper", sharesMapper);
    }

    @Test
    void createShareRejectsAFileTheCallerDoesNotOwn() {
        CreateShareRequest request = new CreateShareRequest();
        request.setFileId(7L);
        when(filesService.getFileInfo(10L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> sharesService.createLinkShare(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资源不存在");

        verifyNoInteractions(sharesMapper, cryptoService);
    }

    @Test
    void createShareStoresOnlyThePasswordHashAndReturnsTheShareCode() {
        CreateShareRequest request = new CreateShareRequest();
        request.setFileId(7L);
        request.setPassword("code-1234");
        request.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(filesService.getFileInfo(10L, 7L)).thenReturn(ownedFile());
        when(cryptoService.generateSecureToken()).thenReturn("safe-share-code");
        when(sharesMapper.selectCount(any())).thenReturn(0L);
        when(cryptoService.hashUserPassword("code-1234")).thenReturn("$2a$hashed-password");
        when(sharesMapper.insert(any(Shares.class))).thenAnswer(invocation -> {
            ((Shares) invocation.getArgument(0)).setId(101L);
            return 1;
        });

        ShareResponse response = sharesService.createLinkShare(10L, request);

        ArgumentCaptor<Shares> shareCaptor = ArgumentCaptor.forClass(Shares.class);
        verify(sharesMapper).insert((Shares) shareCaptor.capture());
        assertThat(shareCaptor.getValue().getPassword()).isEqualTo("$2a$hashed-password");
        assertThat(shareCaptor.getValue().getPassword()).isNotEqualTo("code-1234");
        assertThat(response.getShareCode()).isEqualTo("safe-share-code");
        assertThat(response.isPasswordProtected()).isTrue();
        assertThat(response.toString()).doesNotContain("code-1234", "$2a$hashed-password");
    }

    @Test
    void createShareDefaultsToOneDayExpiryWhenCallerOmitsIt() {
        CreateShareRequest request = new CreateShareRequest();
        request.setFileId(7L);
        request.setPassword("aB3d");
        when(filesService.getFileInfo(10L, 7L)).thenReturn(ownedFile());
        when(cryptoService.generateSecureToken()).thenReturn("safe-share-code");
        when(sharesMapper.selectCount(any())).thenReturn(0L);
        when(cryptoService.hashUserPassword("aB3d")).thenReturn("$2a$hashed-password");
        when(sharesMapper.insert(any(Shares.class))).thenReturn(1);

        LocalDateTime beforeCreate = LocalDateTime.now().plusDays(1).minusSeconds(1);
        sharesService.createLinkShare(10L, request);
        LocalDateTime afterCreate = LocalDateTime.now().plusDays(1).plusSeconds(1);

        ArgumentCaptor<Shares> shareCaptor = ArgumentCaptor.forClass(Shares.class);
        verify(sharesMapper).insert(shareCaptor.capture());
        assertThat(shareCaptor.getValue().getExpiredAt()).isBetween(beforeCreate, afterCreate);
    }

    @Test
    void publicAccessRejectsAnIncorrectPasswordBeforeLoadingTheFile() {
        Shares share = new Shares();
        share.setStatus((byte) 1);
        share.setPassword("$2a$hashed-password");
        share.setShareLink("safe-share-code");
        share.setSharerId(10L);
        share.setFileId(7L);
        when(sharesMapper.selectOne(any())).thenReturn(share);
        when(cryptoService.verifyUserPassword("incorrect", "$2a$hashed-password")).thenReturn(false);
        PublicShareAccessRequest request = new PublicShareAccessRequest();
        request.setPassword("incorrect");

        assertThatThrownBy(() -> sharesService.accessPublicShare("safe-share-code", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("提取码错误");

        verify(filesService, never()).getFileInfo(any(), any());
    }

    @Test
    void publicAccessIssuesAShortLivedMinioDownloadUrlAfterPasswordVerification() {
        Shares share = activeShare();
        when(sharesMapper.selectOne(any())).thenReturn(share);
        when(cryptoService.verifyUserPassword("aB3d", "$2a$hashed-password")).thenReturn(true);
        when(filesService.getFileInfo(10L, 7L)).thenReturn(ownedFile());
        when(fileContentsService.getById(99L)).thenReturn(availableContent());
        when(minIOUtil.parseStoragePath("files/contents/report-object", "files"))
                .thenReturn(new MinIOUtil.ObjectLocation("files", "contents/report-object"));
        when(minIOUtil.presignDownload("files", "contents/report-object", "report.pdf", Duration.ofSeconds(60)))
                .thenReturn("https://minio.example/files/contents/report-object?signed=true");
        PublicShareAccessRequest request = new PublicShareAccessRequest();
        request.setPassword("aB3d");

        var response = sharesService.accessPublicShare("safe-share-code", request);

        assertThat(response.getDownloadUrl()).isEqualTo("https://minio.example/files/contents/report-object?signed=true");
        assertThat(response.getDownloadUrlExpiresInSeconds()).isEqualTo(60);
        verify(minIOUtil).presignDownload("files", "contents/report-object", "report.pdf", Duration.ofSeconds(60));
    }

    private Shares activeShare() {
        Shares share = new Shares();
        share.setId(101L);
        share.setStatus((byte) 1);
        share.setPassword("$2a$hashed-password");
        share.setShareLink("safe-share-code");
        share.setSharerId(10L);
        share.setFileId(7L);
        return share;
    }

    private Files ownedFile() {
        Files file = new Files();
        file.setId(7L);
        file.setOwnerId(10L);
        file.setBucketId(2L);
        file.setContentId(99L);
        file.setFilename("report.pdf");
        file.setIsFolder(false);
        file.setSize(1024L);
        return file;
    }

    private FileContents availableContent() {
        FileContents content = new FileContents();
        content.setId(99L);
        content.setStatus((byte) 1);
        content.setStoragePath("files/contents/report-object");
        return content;
    }
}
