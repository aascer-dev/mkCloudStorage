package com.zjj.mkcscommon.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 加密配置属性
 */
@Component
@Data
@ConfigurationProperties(prefix = "mkcs.crypto")
public class CryptoProperties {

    /**
     * 默认加密盐值
     */
    private String defaultSalt;
    
    /**
     * 默认加密密码
     */
    private String defaultPassword;
    
    /**
     * BCrypt 强度 (4-31, 默认10, 数值越高越安全但速度越慢)
     */
    private Integer bcryptStrength;
}