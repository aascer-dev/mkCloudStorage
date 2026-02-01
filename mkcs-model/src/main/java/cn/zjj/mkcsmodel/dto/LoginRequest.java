package cn.zjj.mkcsmodel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {
    
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度必须在2-50个字符之间")
    private String username;
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100个字符之间")
    private String password;
    
    /**
     * 记住我
     * true: 长期有效（7天）
     * false: 短期有效（6小时）
     */
    private Boolean rememberMe;
    
    /**
     * 验证码（可选）
     */
    private String captcha;
    
    /**
     * 验证码key（可选）
     */
    private String captchaKey;
}