package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分片上传请求")
public class ChunkUploadRequest {

    @NotBlank(message = "上传任务ID不能为空")
    @Schema(description = "上传任务ID")
    private String uploadId;

    @NotNull(message = "分片索引不能为空")
    @Schema(description = "分片索引（从0开始）")
    private Integer chunkIndex;

    @NotNull(message = "总分片数不能为空")
    @Schema(description = "总分片数")
    private Integer totalChunks;

    @NotBlank(message = "分片hash不能为空")
    @Schema(description = "分片内容SHA256 hash")
    private String chunkHash;

    @NotNull(message = "分片大小不能为空")
    @Schema(description = "分片大小（字节）")
    private Long chunkSize;

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "文件名")
    private String filename;

    @NotNull(message = "文件总大小不能为空")
    @Schema(description = "文件总大小（字节）")
    private Long fileSize;

    @NotBlank(message = "文件hash不能为空")
    @Schema(description = "文件内容SHA256 hash")
    private String fileHash;

    @Schema(description = "父文件夹ID")
    private Long parentId;

    @Schema(description = "存储桶ID")
    private Long bucketId;

    @Schema(description = "MIME类型")
    private String mimeType;
}
