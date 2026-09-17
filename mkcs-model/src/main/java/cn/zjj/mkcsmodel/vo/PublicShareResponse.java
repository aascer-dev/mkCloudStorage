package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "公开分享访问响应")
public class PublicShareResponse {

    private String shareCode;
    private boolean passwordProtected;
    private LocalDateTime expiresAt;
    @Schema(description = "MinIO 签发的短时下载地址；链接在有效期内可直接下载")
    private String downloadUrl;
    @Schema(description = "下载地址有效期（秒）")
    private int downloadUrlExpiresInSeconds;
    private FileSummaryResponse file;
}
