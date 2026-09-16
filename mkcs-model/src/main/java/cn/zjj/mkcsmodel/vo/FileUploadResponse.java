package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应VO
 * @author 34978
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传响应")
public class FileUploadResponse {

    @Schema(description = "文件ID")
    private Long fileId;

    @Schema(description = "文件名")
    private String filename;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "是否秒传（文件已存在）")
    private Boolean isSecondUpload;

    @Schema(description = "文件访问URL")
    private String fileUrl;

    @Schema(description = "上传状态：success=成功, duplicate=重复, processing=处理中，not_found=文件不存在")
    private String status;

    @Schema(description = "消息")
    private String message;
}
