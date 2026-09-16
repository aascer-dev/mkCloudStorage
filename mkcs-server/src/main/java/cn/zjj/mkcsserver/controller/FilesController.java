package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.dto.ChunkUploadRequest;
import cn.zjj.mkcsmodel.dto.FileUploadRequest;
import cn.zjj.mkcsmodel.dto.FolderUploadRequest;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import cn.zjj.mkcsserver.service.FilesService;
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

    /**
     * 秒传检查
     * 设计说明：
     * - 前端先计算文件hash
     * - 调用此接口检查文件是否已存在
     * - 如果存在，返回isSecondUpload=true，跳过上传
     */
    @PostMapping("/check")
    @SaCheckLogin
    @Operation(summary = "秒传检查", description = "检查文件是否已存在，支持秒传。前端需要先计算文件hash")
    public Result<FileUploadResponse> checkFileExists(
            @RequestParam("filename") String filename,
            @RequestParam("contentHash") String contentHash,
            @RequestParam("fileSize") Long fileSize,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "bucketId", required = false) Long bucketId,
            @RequestParam(value = "mimeType", required = false) String mimeType) {
        
        log.info("检查文件是否存在: {}, hash: {}", filename, contentHash);
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "单文件上传", description = "上传单个文件，支持秒传。前端直接上传文件，后端自动计算hash并处理秒传")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "bucketId", required = false) Long bucketId) {
        
        log.info("上传文件: {}, 大小: {}", file.getOriginalFilename(), file.getSize());
        Long userId = StpUtil.getLoginIdAsLong();
        
        try {
            FileUploadResponse response = filesService.uploadFile(userId, file, parentId, bucketId);
            String message = response.getIsSecondUpload() ? "文件已存在，秒传成功" : "文件上传成功";
            return Result.success(message, response);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 初始化分片上传
     */
    @PostMapping("/chunk/init")
    @SaCheckLogin
    @Operation(summary = "初始化分片上传", description = "初始化分片上传任务")
    public Result<ChunkUploadResponse> initChunkUpload(@Valid @RequestBody ChunkUploadRequest request) {
        log.info("初始化分片上传: {}, 分片数: {}", request.getFilename(), request.getTotalChunks());
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "上传分片", description = "上传单个分片，支持断点续传")
    public Result<ChunkUploadResponse> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam(value = "chunkHash", required = false) String chunkHash,
            @RequestParam(value = "randomOffset", required = false) Long randomOffset,
            @RequestParam(value = "randomLength", required = false) Integer randomLength,
            @RequestParam(value = "randomHash", required = false) String randomHash) {
        
        log.info("上传分片: uploadId={}, index={}", uploadId, chunkIndex);
        Long userId = StpUtil.getLoginIdAsLong();
        
        try {
            ChunkUploadResponse response = filesService.uploadChunk(
                    userId, uploadId, chunkIndex, chunk, chunkHash,
                    randomOffset, randomLength, randomHash);
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
    @SaCheckLogin
    @Operation(summary = "完成分片上传", description = "合并所有分片")
    public Result<FileUploadResponse> completeChunkUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam(value = "fileHash", required = false) String fileHash) {
        
        log.info("完成分片上传: {}", uploadId);
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "取消分片上传", description = "取消上传任务，清理临时数据")
    public Result<Void> cancelChunkUpload(@RequestParam("uploadId") String uploadId) {
        log.info("取消分片上传: {}", uploadId);
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "创建文件夹", description = "创建单个文件夹")
    public Result<Files> createFolder(@Valid @RequestBody FolderUploadRequest request) {
        log.info("创建文件夹: {}", request.getFolderName());
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "批量创建文件夹", description = "支持嵌套路径创建多级文件夹")
    public Result<Files> batchCreateFolders(@Valid @RequestBody FolderUploadRequest request) {
        log.info("批量创建文件夹: {}", request.getFolderPath());
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "获取文件详情", description = "获取文件的详细信息")
    public Result<Files> getFileInfo(@PathVariable Long fileId) {
        log.info("获取文件详情: {}", fileId);
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "获取文件夹内容", description = "获取文件夹下的所有文件和子文件夹")
    public Result<List<Files>> getFolderContents(
            @PathVariable Long folderId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        
        log.info("获取文件夹内容: folderId={}", folderId);
        Long userId = StpUtil.getLoginIdAsLong();
        
        try {
            List<Files> contents = filesService.getFolderContents(userId, folderId, pageNum, pageSize);
            return Result.success("获取成功", contents);
        } catch (Exception e) {
            log.error("获取文件夹内容失败", e);
            return Result.error("获取文件夹内容失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    @SaCheckLogin
    @Operation(summary = "删除文件", description = "删除文件或文件夹（软删除）")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        log.info("删除文件: {}", fileId);
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "批量删除文件", description = "批量删除多个文件或文件夹")
    public Result<Void> batchDeleteFiles(@RequestBody List<Long> fileIds) {
        log.info("批量删除文件: count={}", fileIds.size());
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "重命名文件", description = "重命名文件或文件夹")
    public Result<Files> renameFile(
            @PathVariable Long fileId,
            @RequestParam("newName") String newName) {
        
        log.info("重命名文件: fileId={}, newName={}", fileId, newName);
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "移动文件", description = "将文件移动到其他文件夹")
    public Result<Files> moveFile(
            @PathVariable Long fileId,
            @RequestParam("targetParentId") Long targetParentId) {
        
        log.info("移动文件: fileId={}, targetParentId={}", fileId, targetParentId);
        Long userId = StpUtil.getLoginIdAsLong();
        
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
    @SaCheckLogin
    @Operation(summary = "搜索文件", description = "按文件名搜索文件")
    public Result<List<Files>> searchFiles(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        
        log.info("搜索文件: keyword={}", keyword);
        Long userId = StpUtil.getLoginIdAsLong();
        
        try {
            List<Files> files = filesService.searchFiles(userId, keyword, pageNum, pageSize);
            return Result.success("搜索成功", files);
        } catch (Exception e) {
            log.error("搜索文件失败", e);
            return Result.error("搜索文件失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     */
    @GetMapping("/download/{fileId}")
    @SaCheckLogin
    @Operation(summary = "下载文件", description = "根据文件ID下载文件")
    public void downloadFile(@PathVariable Long fileId, HttpServletResponse response) {
        log.info("下载文件: fileId={}", fileId);
        Long userId = StpUtil.getLoginIdAsLong();
        filesService.downloadFile(userId, fileId, response);
    }
}
