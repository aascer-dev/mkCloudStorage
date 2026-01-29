package com.zjj.mkcscommon.utils;

import com.zjj.mkcscommon.properties.CryptoProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 使用Spring Security Crypto的加密工具类
 */
@RequiredArgsConstructor
@Slf4j
@Getter
public class CryptoUtil {

    private final PasswordEncoder passwordEncoder;
    private final CryptoProperties cryptoProperties;

    @PostConstruct
    public void init() {
        log.info("初始化 CryptoUtil，BCrypt强度: {}", cryptoProperties.getBcryptStrength());
    }

    /**
     * 使用BCrypt哈希密码
     */
    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 验证密码与哈希值是否匹配
     */
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    /**
     * 生成安全随机盐值
     */
    public String generateSalt() {
        return KeyGenerators.string().generateKey();
    }

    /**
     * 生成指定长度的安全随机盐值
     */
    public String generateSalt(int length) {
        byte[] salt = new byte[length];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * 使用默认密钥加密文本
     */
    public String encryptText(String plainText) {
        return encryptText(plainText, cryptoProperties.getDefaultPassword(), cryptoProperties.getDefaultSalt());
    }

    /**
     * 使用默认密钥解密文本
     */
    public String decryptText(String encryptedText) {
        return decryptText(encryptedText, cryptoProperties.getDefaultPassword(), cryptoProperties.getDefaultSalt());
    }

    /**
     * 使用自定义密钥和盐值加密文本
     */
    public String encryptText(String plainText, String password, String salt) {
        try {
            TextEncryptor encryptor = Encryptors.text(password, salt);
            return encryptor.encrypt(plainText);
        } catch (Exception e) {
            throw new RuntimeException("文本加密失败", e);
        }
    }

    /**
     * 使用自定义密钥和盐值解密文本
     */
    public String decryptText(String encryptedText, String password, String salt) {
        try {
            TextEncryptor encryptor = Encryptors.text(password, salt);
            return encryptor.decrypt(encryptedText);
        } catch (Exception e) {
            throw new RuntimeException("文本解密失败", e);
        }
    }

    /**
     * 生成AES密钥
     */
    public String generateAESKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("AES密钥生成失败", e);
        }
    }

    /**
     * 使用AES加密数据
     */
    public String encryptAES(String plainText, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 使用AES解密数据
     */
    public String decryptAES(String encryptedText, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 生成安全随机令牌
     */
    public String generateSecureToken() {
        return KeyGenerators.string().generateKey();
    }

    /**
     * 生成指定长度的安全随机令牌
     */
    public String generateSecureToken(int length) {
        byte[] token = new byte[length];
        new SecureRandom().nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}