package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.vo.ChunkUploadResponse;
import cn.zjj.mkcsmodel.vo.FileUploadResponse;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一 服务类
 * 扩展功能：文件上传、分片上传、文件去重、秒传、文件夹上传
 * @author 34978
 */
public interface FilesService extends IService<Files> {

    /**
     * 检查文件是否存在（秒传检查）
     * @param userId 用户ID
     * @param filename 文件名
     * @param contentHash 文件内容hash（MD5/SHA256）
     * @param fileSize 文件大小
     * @param parentId 父目录ID
     * @param bucketId 存储桶ID
     * @param mimeType 文件MIME类型
     */
    FileUploadResponse checkFileExists(
            Long userId,
            String filename,
            String contentHash,
            Long fileSize,
            Long parentId,
            Long bucketId,
            String mimeType
    );

    /**
     * 单文件上传
     * @param userId 用户ID
     * @param file 文件对象
     * @param parentId 父目录ID
     * @param bucketId 存储桶ID
     */
    FileUploadResponse uploadFile(
            Long userId,
            MultipartFile file,
            Long parentId,
            Long bucketId
    );

    /**
     * 初始化分片上传
     */
    ChunkUploadResponse initChunkUpload(
            Long userId,
            String filename,
            Long fileSize,
            String fileHash,
            Integer totalChunks,
            Long parentId,
            Long bucketId,
            String mimeType
    );

    /**
     * 上传单个分片（支持随机位置校验）
     */
    ChunkUploadResponse uploadChunk(
            Long userId,
            String uploadId,
            Integer chunkIndex,
            MultipartFile chunk,
            String chunkHash,
            Long randomOffset,
            Integer randomLength,
            String randomHash
    );

    /**
     * 完成分片上传
     */
    FileUploadResponse completeChunkUpload(
            Long userId,
            String uploadId,
            String fileHash
    );

    /**
     * 取消分片上传
     */
    void cancelChunkUpload(Long userId, String uploadId);

    /**
     * 创建文件夹
     */
    Files createFolder(
            Long userId,
            String folderName,
            Long parentId,
            Long bucketId
    );

    /**
     * 批量创建文件夹（支持嵌套路径）
     */
    Files batchCreateFolders(
            Long userId,
            String folderPath,
            Long parentId,
            Long bucketId
    );

    /**
     * 获取文件详情
     */
    Files getFileInfo(Long userId, Long fileId);

    /**
     * 获取文件夹内容
     */
    List<Files> getFolderContents(Long userId, Long folderId, Integer pageNum, Integer pageSize);

    /**
     * 删除文件
     */
    void deleteFile(Long userId, Long fileId);

    /**
     * 批量删除文件
     */
    void batchDeleteFiles(Long userId, List<Long> fileIds);

    /**
     * 重命名文件
     */
    Files renameFile(Long userId, Long fileId, String newName);

    /**
     * 移动文件
     */
    Files moveFile(Long userId, Long fileId, Long targetParentId);

    /**
     * 搜索文件
     */
    List<Files> searchFiles(Long userId, String keyword, Integer pageNum, Integer pageSize);

    /**
     * 下载文件（流式输出到 HttpServletResponse）
     */
    void downloadFile(Long userId, Long fileId, HttpServletResponse response);
}
