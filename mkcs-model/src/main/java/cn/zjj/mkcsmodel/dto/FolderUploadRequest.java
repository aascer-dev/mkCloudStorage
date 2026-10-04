package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件夹上传请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件夹上传请求")
public class FolderUploadRequest {

    @NotBlank(message = "文件夹名不能为空")
    @Schema(description = "文件夹名")
    private String folderName;

    @Schema(description = "父文件夹ID，为null表示根目录")
    private Long parentId;

    @Schema(description = "存储桶ID")
    private Long bucketId;

    @Schema(description = "文件夹路径（相对路径，用于批量创建）")
    private String folderPath;
}
