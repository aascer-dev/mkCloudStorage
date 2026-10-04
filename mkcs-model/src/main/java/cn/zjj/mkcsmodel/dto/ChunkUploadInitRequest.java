package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 初始化分片上传请求。
 *
 * <p>此请求只描述待上传文件；上传任务 ID 和分片元数据在初始化后才会产生。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "初始化分片上传请求")
public class ChunkUploadInitRequest {

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "文件名")
    private String filename;

    @NotNull(message = "文件总大小不能为空")
    @Positive(message = "文件总大小必须大于0")
    @Schema(description = "文件总大小（字节）")
    private Long fileSize;

    @NotBlank(message = "文件hash不能为空")
    @Schema(description = "文件内容SHA256 hash")
    private String fileHash;

    @NotNull(message = "总分片数不能为空")
    @Positive(message = "总分片数必须大于0")
    @Schema(description = "总分片数")
    private Integer totalChunks;

    @Schema(description = "父文件夹ID")
    private Long parentId;

    @Schema(description = "存储桶ID")
    private Long bucketId;

    @Schema(description = "MIME类型")
    private String mimeType;
}
