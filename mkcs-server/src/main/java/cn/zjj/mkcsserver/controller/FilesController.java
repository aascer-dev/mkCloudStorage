package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.dto.ChunkPartUploadRequest;
import cn.zjj.mkcsmodel.dto.ChunkUploadInitRequest;
import cn.zjj.mkcsmodel.dto.FileIdBatchRequest;
import cn.zjj.mkcsmodel.dto.FileUploadRequest;
import cn.zjj.mkcsmodel.dto.FolderUploadRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadCompleteRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadInitRequest;
import cn.zjj.mkcsmodel.dto.MultipartUploadPresignRequest;
import cn.zjj.mkcsmodel.dto.MultipartSecondUploadVerifyRequest;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import cn.zjj.mkcsmodel.vo.FilePreviewUrlResponse;
import cn.zjj.mkcsmodel.vo.FileSummaryResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadInitResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadPresignResponse;
import cn.zjj.mkcsmodel.vo.MultipartUploadStatusResponse;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsserver.service.FilesService;
import cn.zjj.mkcsserver.service.FileFavoritesService;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * 文件管理控制器
 * 支持：文件上传、分片上传、文件去重、秒传、文件夹管理
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "文件上传、下载、管理等操作")
public class FilesController {

    private final FilesService filesService;
    private final FileFavoritesService fileFavoritesService;

    /**
     * 秒传检查
     * 设计说明：
     * - 前端先计算文件hash
     * - 调用此接口检查文件是否已存在
     * - 如果存在，返回isSecondUpload=true，跳过上传
     */
    @PostMapping("/check")
    @Operation(summary = "秒传检查", description = "检查文件是否已存在，支持秒传。前端需要先计算文件hash")
    public Result<FileUploadResponse> checkFileExists(
            @RequestParam("filename") String filename,
            @RequestParam("contentHash") String contentHash,
            @RequestParam("fileSize") Long fileSize,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "bucketId", required = false) Long bucketId,
            @RequestParam(value = "mimeType", required = false) String mimeType) {
        
        log.info("检查文件是否存在: {}, hash: {}", filename, contentHash);
        Long userId = UserContext.requireUserId();
        
        try {
            FileUploadResponse response = filesService.checkFileExists(
                    userId, filename, contentHash, fileSize, parentId, bucketId, mimeType);
            String message = response.getIsSecondUpload() ? "文件已存在，支持秒传" : "文件不存在，需要上传";
            return Result.success(message, response);
        } catch (Exception e) {
            log.error("秒传检查失败", e);
            return Result.error("秒传检查失败: " + e.getMessage());
        }
    }

    /**
     * 单文件上传
     * 设计说明：
     * - 前端直接上传文件，不计算hash（避免安全隐患）
     * - 后端计算hash并自动处理秒传
     * - 响应中包含isSecondUpload标志
     */
    @PostMapping("/upload")
    @Operation(summary = "单文件上传", description = "上传单个文件，支持秒传。前端直接上传文件，后端自动计算hash并处理秒传")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "bucketId", required = false) Long bucketId) {
        
        log.info("上传文件: {}, 大小: {}", file.getOriginalFilename(), file.getSize());
        Long userId = UserContext.requireUserId();
        
        try {
            FileUploadResponse response = filesService.uploadFile(userId, file, parentId, bucketId);
            String message = response.getIsSecondUpload() ? "文件已存在，秒传成功" : "文件上传成功";
            return Result.success(message, response);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/multipart/init")
    @Operation(summary = "初始化直传分片上传", description = "创建 MinIO Multipart 上传任务；文件数据不经过应用服务")
    public Result<MultipartUploadInitResponse> initMultipartUpload(@Valid @RequestBody MultipartUploadInitRequest request) {
        return Result.success("Multipart上传任务已初始化", filesService.initMultipartUpload(UserContext.requireUserId(), request));
    }

    @PostMapping("/multipart/second-upload/verify")
    @Operation(summary = "验证直传秒传随机切片", description = "校验本地随机切片摘要，成功后创建文件引用")
    public Result<FileUploadResponse> verifyMultipartSecondUpload(
            @Valid @RequestBody MultipartSecondUploadVerifyRequest request) {
        return Result.success("秒传验证通过", filesService.verifyMultipartSecondUpload(UserContext.requireUserId(), request));
    }

    @PostMapping("/multipart/presign")
    @Operation(summary = "获取分片直传地址", description = "为指定分片生成短期有效的 MinIO UploadPart 地址")
    public Result<MultipartUploadPresignResponse> presignMultipartPart(@Valid @RequestBody MultipartUploadPresignRequest request) {
        return Result.success("分片上传地址已生成", filesService.presignMultipartPart(UserContext.requireUserId(), request));
    }

    @GetMapping("/multipart/{uploadId}/status")
    @Operation(summary = "查询直传分片进度", description = "从 MinIO 查询已上传分片，用于断点续传")
    public Result<MultipartUploadStatusResponse> getMultipartUploadStatus(@PathVariable Long uploadId) {
        return Result.success("上传状态获取成功", filesService.getMultipartUploadStatus(UserContext.requireUserId(), uploadId));
    }

    @PostMapping("/multipart/complete")
    @Operation(summary = "完成直传分片上传", description = "校验 MinIO 已上传分片并完成对象存储端合并")
    public Result<FileUploadResponse> completeMultipartUpload(@Valid @RequestBody MultipartUploadCompleteRequest request) {
        return Result.success("文件上传完成", filesService.completeMultipartUpload(UserContext.requireUserId(), request));
    }

    @DeleteMapping("/multipart/{uploadId}")
    @Operation(summary = "取消直传分片上传", description = "中止 MinIO Multipart 上传并取消任务")
    public Result<Void> cancelMultipartUpload(@PathVariable Long uploadId) {
        filesService.cancelMultipartUpload(UserContext.requireUserId(), uploadId);
        return Result.success("上传已取消", null);
    }

    /**
     * 初始化分片上传
     */
    @PostMapping("/chunk/init")
    @Operation(summary = "初始化分片上传", description = "初始化分片上传任务")
    public Result<ChunkUploadResponse> initChunkUpload(@Valid @RequestBody ChunkUploadInitRequest request) {
        log.info("初始化分片上传: {}, 分片数: {}", request.getFilename(), request.getTotalChunks());
        Long userId = UserContext.requireUserId();
        
        try {
            ChunkUploadResponse response = filesService.initChunkUpload(
                userId, request.getFilename(), request.getFileSize(),
                request.getFileHash(), request.getTotalChunks(),
                request.getParentId(), request.getBucketId(), request.getMimeType()
            );
            return Result.success("分片上传初始化成功", response);
        } catch (Exception e) {
            log.error("初始化分片上传失败", e);
            return Result.error("初始化分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传分片
     */
    @PostMapping("/chunk/upload")
    @Operation(summary = "上传分片", description = "上传单个分片，支持断点续传；分片元数据和二进制内容使用 multipart/form-data 传输")
    public Result<ChunkUploadResponse> uploadChunk(
            @Valid @ModelAttribute ChunkPartUploadRequest request,
            @RequestParam("chunk") MultipartFile chunk) {
        
        log.info("上传分片: uploadId={}, index={}", request.getUploadId(), request.getChunkIndex());
        Long userId = UserContext.requireUserId();
        
        try {
            ChunkUploadResponse response = filesService.uploadChunk(
                    userId, request.getUploadId(), request.getChunkIndex(), chunk, request.getChunkHash(),
                    request.getRandomOffset(), request.getRandomLength(), request.getRandomHash());
            return Result.success(response.getIsComplete() ? "文件上传完成" : "分片上传成功", response);
        } catch (Exception e) {
            log.error("分片上传失败", e);
            return Result.error("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 完成分片上传
     */
    @PostMapping("/chunk/complete")
    @Operation(summary = "完成分片上传", description = "合并所有分片")
    public Result<FileUploadResponse> completeChunkUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam(value = "fileHash", required = false) String fileHash) {
        
        log.info("完成分片上传: {}", uploadId);
        Long userId = UserContext.requireUserId();
        
        try {
            FileUploadResponse response = filesService.completeChunkUpload(userId, uploadId, fileHash);
            return Result.success("文件上传完成", response);
        } catch (Exception e) {
            log.error("完成分片上传失败", e);
            return Result.error("完成分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 取消分片上传
     */
    @DeleteMapping("/chunk/cancel")
    @Operation(summary = "取消分片上传", description = "取消上传任务，清理临时数据")
    public Result<Void> cancelChunkUpload(@RequestParam("uploadId") String uploadId) {
        log.info("取消分片上传: {}", uploadId);
        Long userId = UserContext.requireUserId();
        
        try {
            filesService.cancelChunkUpload(userId, uploadId);
            return Result.success("分片上传已取消", null);
        } catch (Exception e) {
            log.error("取消分片上传失败", e);
            return Result.error("取消分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 创建文件夹
     */
    @PostMapping("/folder/create")
    @Operation(summary = "创建文件夹", description = "创建单个文件夹")
    public Result<Files> createFolder(@Valid @RequestBody FolderUploadRequest request) {
        log.info("创建文件夹: {}", request.getFolderName());
        Long userId = UserContext.requireUserId();
        
        try {
            Files folder = filesService.createFolder(
                userId, request.getFolderName(), 
                request.getParentId(), request.getBucketId()
            );
            return Result.success("文件夹创建成功", folder);
        } catch (Exception e) {
            log.error("文件夹创建失败", e);
            return Result.error("文件夹创建失败: " + e.getMessage());
        }
    }

    /**
     * 批量创建文件夹
     */
    @PostMapping("/folder/batch-create")
    @Operation(summary = "批量创建文件夹", description = "支持嵌套路径创建多级文件夹")
    public Result<Files> batchCreateFolders(@Valid @RequestBody FolderUploadRequest request) {
        log.info("批量创建文件夹: {}", request.getFolderPath());
        Long userId = UserContext.requireUserId();
        
        try {
            Files folder = filesService.batchCreateFolders(
                userId, request.getFolderPath(),
                request.getParentId(), request.getBucketId()
            );
            return Result.success("文件夹批量创建成功", folder);
        } catch (Exception e) {
            log.error("文件夹批量创建失败", e);
            return Result.error("文件夹批量创建失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件详情
     */
    @GetMapping("/{fileId}")
    @Operation(summary = "获取文件详情", description = "获取文件的详细信息")
    public Result<Files> getFileInfo(@PathVariable Long fileId) {
        log.info("获取文件详情: {}", fileId);
        Long userId = UserContext.requireUserId();
        
        try {
            Files file = filesService.getFileInfo(userId, fileId);
            return file != null ? Result.success("获取成功", file) : Result.error("文件不存在");
        } catch (Exception e) {
            log.error("获取文件详情失败", e);
            return Result.error("获取文件详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件夹内容
     */
    @GetMapping("/folder/{folderId}/contents")
    @Operation(summary = "获取文件夹内容", description = "获取文件夹下的所有文件和子文件夹；folderId 为 0 表示根目录")
    public Result<List<FileSummaryResponse>> getFolderContents(
            @PathVariable Long folderId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        
        log.info("获取文件夹内容: folderId={}", folderId);
        Long userId = UserContext.requireUserId();
        
        try {
            List<Files> contents = filesService.getFolderContents(userId, folderId, pageNum, pageSize);
            return Result.success("获取成功", toFileSummaries(userId, contents));
        } catch (Exception e) {
            log.error("获取文件夹内容失败", e);
            return Result.error("获取文件夹内容失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除文件", description = "删除文件或文件夹（软删除）")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        log.info("删除文件: {}", fileId);
        Long userId = UserContext.requireUserId();
        
        try {
            filesService.deleteFile(userId, fileId);
            return Result.success("文件删除成功", null);
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return Result.error("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除文件
     */
    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除文件", description = "批量删除多个文件或文件夹")
    public Result<Void> batchDeleteFiles(@RequestBody List<Long> fileIds) {
        log.info("批量删除文件: count={}", fileIds.size());
        Long userId = UserContext.requireUserId();
        
        try {
            filesService.batchDeleteFiles(userId, fileIds);
            return Result.success("文件批量删除成功", null);
        } catch (Exception e) {
            log.error("文件批量删除失败", e);
            return Result.error("文件批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 重命名文件
     */
    @PutMapping("/{fileId}/rename")
    @Operation(summary = "重命名文件", description = "重命名文件或文件夹")
    public Result<Files> renameFile(
            @PathVariable Long fileId,
            @RequestParam("newName") String newName) {
        
        log.info("重命名文件: fileId={}, newName={}", fileId, newName);
        Long userId = UserContext.requireUserId();
        
        try {
            Files file = filesService.renameFile(userId, fileId, newName);
            return Result.success("文件重命名成功", file);
        } catch (Exception e) {
            log.error("文件重命名失败", e);
            return Result.error("文件重命名失败: " + e.getMessage());
        }
    }

    /**
     * 移动文件
     */
    @PutMapping("/{fileId}/move")
    @Operation(summary = "移动文件", description = "将文件移动到其他文件夹")
    public Result<Files> moveFile(
            @PathVariable Long fileId,
            @RequestParam("targetParentId") Long targetParentId) {
        
        log.info("移动文件: fileId={}, targetParentId={}", fileId, targetParentId);
        Long userId = UserContext.requireUserId();
        
        try {
            Files file = filesService.moveFile(userId, fileId, targetParentId);
            return Result.success("文件移动成功", file);
        } catch (Exception e) {
            log.error("文件移动失败", e);
            return Result.error("文件移动失败: " + e.getMessage());
        }
    }

    /**
     * 搜索文件
     */
    @GetMapping("/search")
    @Operation(summary = "搜索文件", description = "按文件名搜索文件")
    public Result<List<FileSummaryResponse>> searchFiles(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        
        log.info("搜索文件: keyword={}", keyword);
        Long userId = UserContext.requireUserId();
        
        try {
            List<Files> files = filesService.searchFiles(userId, keyword, pageNum, pageSize);
            return Result.success("搜索成功", toFileSummaries(userId, files));
        } catch (Exception e) {
            log.error("搜索文件失败", e);
            return Result.error("搜索文件失败: " + e.getMessage());
        }
    }

    private List<FileSummaryResponse> toFileSummaries(Long userId, List<Files> files) {
        Set<Long> favoriteFileIds = fileFavoritesService.getFavoriteFileIds(userId);
        return files.stream()
                .map(file -> FileSummaryResponse.from(file, favoriteFileIds.contains(file.getId())))
                .toList();
    }

    /**
     * 下载文件
     */
    @GetMapping("/download/{fileId}")
    @Operation(summary = "下载文件", description = "根据文件ID下载文件")
    public void downloadFile(@PathVariable Long fileId, HttpServletResponse response) {
        log.info("下载文件: fileId={}", fileId);
        Long userId = UserContext.requireUserId();
        filesService.downloadFile(userId, fileId, response);
    }

    @GetMapping("/recycle-bin")
    @Operation(summary = "获取回收站", description = "获取当前用户已软删除的文件和文件夹")
    public Result<List<FileSummaryResponse>> getRecycleBinFiles() {
        List<FileSummaryResponse> files = filesService.getRecycleBinFiles(UserContext.requireUserId())
                .stream()
                .map(FileSummaryResponse::from)
                .toList();
        return Result.success("获取回收站成功", files);
    }

    @PostMapping("/recycle-bin/restore")
    @Operation(summary = "还原回收站文件", description = "恢复选中的文件或文件夹及其已删除子项")
    public Result<Void> restoreRecycleBinFiles(@Valid @RequestBody FileIdBatchRequest request) {
        filesService.restoreFiles(UserContext.requireUserId(), request.getFileIds());
        return Result.success("文件已还原", null);
    }

    @DeleteMapping("/recycle-bin")
    @Operation(summary = "永久删除回收站文件", description = "永久删除选中的已删除文件或文件夹")
    public Result<Void> permanentlyDeleteRecycleBinFiles(@Valid @RequestBody FileIdBatchRequest request) {
        filesService.permanentlyDeleteFiles(UserContext.requireUserId(), request.getFileIds());
        return Result.success("文件已永久删除", null);
    }

    @PostMapping("/{fileId}/preview-url")
    @Operation(summary = "获取文件预览地址", description = "创建短期有效、支持浏览器 Range 请求的预览地址")
    public Result<FilePreviewUrlResponse> createPreviewUrl(@PathVariable Long fileId) {
        return Result.success("预览地址已生成", filesService.createPreviewUrl(UserContext.requireUserId(), fileId));
    }

    @GetMapping("/preview/{fileId}")
    @Operation(summary = "预览文件", description = "使用短期预览凭证以内联模式流式返回文件，并支持单个字节范围请求")
    public void previewFile(@PathVariable Long fileId,
                            @RequestParam("ticket") String ticket,
                            @RequestHeader(value = "Range", required = false) String rangeHeader,
                            HttpServletResponse response) {
        filesService.previewFile(fileId, ticket, rangeHeader, response);
    }
}
