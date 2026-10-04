package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "访问受保护分享的请求")
public class PublicShareAccessRequest {

    @Pattern(regexp = "^[A-Za-z0-9]{4}$", message = "提取码必须为4位大小写字母或数字")
    @Schema(description = "4位大小写字母或数字提取码")
    private String password;
}
