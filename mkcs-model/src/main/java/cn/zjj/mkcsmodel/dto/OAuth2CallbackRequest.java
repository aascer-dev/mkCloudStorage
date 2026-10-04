package cn.zjj.mkcsmodel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth2 回调请求
 */
@Data
public class OAuth2CallbackRequest {
    
    /**
     * OAuth2 授权码
     */
    @NotBlank(message = "授权码不能为空")
    private String code;
    
    /**
     * OAuth2 state 参数（用于防止 CSRF 攻击）
     */
    private String state;
    
    /**
     * 是否记住登录状态
     */
    private Boolean rememberMe;
}
