package cn.zjj.mkcsmodel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送验证码请求
 */
@Data
@Schema(description = "发送验证码请求")
public class SendVerificationCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;

    @NotBlank(message = "验证码类型不能为空")
    @Schema(description = "验证码类型：REGISTER-注册, RESET_PASSWORD-重置密码, LOGIN-登录验证", 
            example = "REGISTER", 
            allowableValues = {"REGISTER", "RESET_PASSWORD", "LOGIN"})
    private String type;
}
