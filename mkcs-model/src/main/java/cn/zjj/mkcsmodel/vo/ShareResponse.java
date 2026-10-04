package cn.zjj.mkcsmodel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "我的链接分享响应")
public class ShareResponse {

    private Long id;
    private String shareCode;
    private boolean passwordProtected;
    private LocalDateTime expiresAt;
    private Byte status;
    private LocalDateTime createdAt;
    private FileSummaryResponse file;
}
