package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "初始化对象存储 Multipart 上传请求")
public class MultipartUploadInitRequest {
    @NotBlank(message = "文件名不能为空")
    private String filename;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于0")
    private Long fileSize;

    @NotBlank(message = "文件SHA-256不能为空")
    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "文件SHA-256格式不正确")
    private String fileHash;

    private Long parentId;
    private Long bucketId;
    private String mimeType;
}
