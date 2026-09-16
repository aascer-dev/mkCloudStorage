package cn.zjj.mkcsmodel.dto;

import com.zjj.mkcscommon.enumeration.VerificationCodeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * @author 34978
 * 重置密码请求 DTO
 * 对应截图中：邮箱 + 验证码 + 新密码
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    // 验证码必须为6位数字
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6~64之间")
    private String password;

    @NotBlank(message = "验证码类型不能为空")
    private String verificationCodeType;
}