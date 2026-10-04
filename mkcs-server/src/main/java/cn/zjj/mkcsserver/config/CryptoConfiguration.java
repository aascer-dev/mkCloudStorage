package cn.zjj.mkcsserver.config;

import com.zjj.mkcscommon.properties.CryptoProperties;
import com.zjj.mkcscommon.utils.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置类
 */
@Configuration
@Slf4j
public class CryptoConfiguration {

    /**
     * 配置BCrypt密码编码器
     * BCrypt是专为密码哈希设计的强哈希函数
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder(CryptoProperties cryptoProperties) {
        Integer strength = cryptoProperties.getBcryptStrength();
        if (strength == null || strength < 4 || strength > 31) {
            throw new IllegalArgumentException("BCrypt强度必须在4-31之间，当前值: " + strength);
        }
        log.info("创建 BCryptPasswordEncoder，强度: {}", strength);
        return new BCryptPasswordEncoder(strength);
    }

    /**
     * 创建CryptoUtil对象
     */
    @Bean
    @ConditionalOnMissingBean
    public CryptoUtil cryptoUtil(PasswordEncoder passwordEncoder, CryptoProperties cryptoProperties) {
        // 验证必填配置
        if (cryptoProperties.getDefaultSalt() == null || cryptoProperties.getDefaultSalt().trim().isEmpty()) {
            throw new IllegalArgumentException("默认加密盐值不能为空");
        }
        if (cryptoProperties.getDefaultPassword() == null || cryptoProperties.getDefaultPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("默认加密密码不能为空");
        }
        
        log.info("开始创建CryptoUtil对象，Bcrypt强度: {}", cryptoProperties.getBcryptStrength());
        return new CryptoUtil(passwordEncoder, cryptoProperties);
    }
}