package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsserver.auth.AuthenticatedUser;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsmodel.vo.FilePreviewUrlResponse;
import cn.zjj.mkcsserver.service.FilesService;
import cn.zjj.mkcsserver.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FilesControllerChunkRequestTest {

    @Mock
    private FilesService filesService;

    @InjectMocks
    private FilesController filesController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(filesController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void initChunkUploadAcceptsOnlyInitializationMetadata() throws Exception {
        when(filesService.initChunkUpload(eq(42L), eq("archive.zip"), eq(1024L),
                eq("file-hash"), eq(2), eq(7L), eq(9L), eq("application/zip")))
                .thenReturn(ChunkUploadResponse.builder().uploadId("upload-1").build());

        UserContext.set(new AuthenticatedUser(42L, "user", 1L));
        try {
            mockMvc.perform(MockMvcRequestBuilders.post("/api/files/chunk/init")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "filename": "archive.zip",
                                      "fileSize": 1024,
                                      "fileHash": "file-hash",
                                      "totalChunks": 2,
                                      "parentId": 7,
                                      "bucketId": 9,
                                      "mimeType": "application/zip"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.uploadId").value("upload-1"));
        } finally { UserContext.clear(); }

        verify(filesService).initChunkUpload(42L, "archive.zip", 1024L,
                "file-hash", 2, 7L, 9L, "application/zip");
    }

    @Test
    void previewUrlRequiresTheAuthenticatedOwnerAndReturnsOnlyTheShortLivedUrl() throws Exception {
        when(filesService.createPreviewUrl(42L, 7L)).thenReturn(FilePreviewUrlResponse.builder()
                .previewUrl("/api/files/preview/7?ticket=opaque-ticket")
                .expiresInSeconds(300)
                .build());

        UserContext.set(new AuthenticatedUser(42L, "user", 1L));
        try {
            mockMvc.perform(MockMvcRequestBuilders.post("/api/files/7/preview-url"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.previewUrl").value("/api/files/preview/7?ticket=opaque-ticket"))
                    .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        } finally { UserContext.clear(); }

        verify(filesService).createPreviewUrl(42L, 7L);
    }

    @Test
    void uploadChunkBindsMultipartMetadataToPartRequest() throws Exception {
        MockMultipartFile chunk = new MockMultipartFile("chunk", "part-0", "application/octet-stream", new byte[]{1, 2, 3});
        when(filesService.uploadChunk(eq(42L), eq("upload-1"), eq(0), eq(chunk), eq("chunk-hash"),
                eq(0L), eq(3), eq("random-hash")))
                .thenReturn(ChunkUploadResponse.builder().isComplete(false).build());

        UserContext.set(new AuthenticatedUser(42L, "user", 1L));
        try {
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/files/chunk/upload")
                            .file(chunk)
                            .param("uploadId", "upload-1")
                            .param("chunkIndex", "0")
                            .param("chunkHash", "chunk-hash")
                            .param("randomOffset", "0")
                            .param("randomLength", "3")
                            .param("randomHash", "random-hash"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        } finally { UserContext.clear(); }

        verify(filesService).uploadChunk(42L, "upload-1", 0, chunk, "chunk-hash", 0L, 3, "random-hash");
    }

    @Test
    void uploadChunkRejectsMissingTaskIdBeforeCallingService() throws Exception {
        MockMultipartFile chunk = new MockMultipartFile("chunk", "part-0", "application/octet-stream", new byte[]{1});

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/files/chunk/upload")
                        .file(chunk)
                        .param("chunkIndex", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(422));

        verifyNoInteractions(filesService);
    }

    @Test
    void uploadChunkRejectsIncompleteRandomChecksumMetadata() throws Exception {
        MockMultipartFile chunk = new MockMultipartFile("chunk", "part-0", "application/octet-stream", new byte[]{1});

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/files/chunk/upload")
                        .file(chunk)
                        .param("uploadId", "upload-1")
                        .param("chunkIndex", "0")
                        .param("randomOffset", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(422));

        verifyNoInteractions(filesService);
    }
}
