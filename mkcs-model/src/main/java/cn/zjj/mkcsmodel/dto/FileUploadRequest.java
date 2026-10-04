package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传请求")
public class FileUploadRequest {

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "文件名")
    private String filename;

    @NotNull(message = "文件大小不能为空")
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @NotBlank(message = "文件内容hash不能为空")
    @Schema(description = "文件内容MD5 hash")
    private String contentHash;

    @Schema(description = "父文件夹ID，为null表示根目录")
    private Long parentId;

    @Schema(description = "存储桶ID")
    private Long bucketId;

    @Schema(description = "MIME类型")
    private String mimeType;
}
