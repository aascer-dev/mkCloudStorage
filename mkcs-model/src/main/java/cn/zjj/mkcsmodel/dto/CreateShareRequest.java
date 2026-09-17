package cn.zjj.mkcsmodel.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "创建链接分享请求")
public class CreateShareRequest {

    @NotNull(message = "文件ID不能为空")
    @Schema(description = "要分享的文件或文件夹ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fileId;

    @NotBlank(message = "提取码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{4}$", message = "提取码必须为4位大小写字母或数字")
    @Schema(description = "必填的4位大小写字母或数字提取码；仅保存 BCrypt 哈希", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Future(message = "过期时间必须晚于当前时间")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "过期时间；为空时服务端默认设置为创建后1天")
    private LocalDateTime expiresAt;
}
