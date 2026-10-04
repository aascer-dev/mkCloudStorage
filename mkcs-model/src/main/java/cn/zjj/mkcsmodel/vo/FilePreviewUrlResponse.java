package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/** Short-lived URL that authorizes native browser preview requests. */
@Data
@Builder
@Schema(description = "文件预览地址响应")
public class FilePreviewUrlResponse {

    @Schema(description = "支持 Range 请求的短期预览地址")
    private String previewUrl;

    @Schema(description = "地址有效期（秒）")
    private Integer expiresInSeconds;
}
