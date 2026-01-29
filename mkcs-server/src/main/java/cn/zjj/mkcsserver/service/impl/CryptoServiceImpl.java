package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsserver.service.CryptoService;
import com.zjj.mkcscommon.utils.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 加密服务实现类
 * 处理加密和解密操作的具体实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoServiceImpl implements CryptoService {

    private final CryptoUtil cryptoUtil;

    @Override
    public String hashUserPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        log.debug("正在哈希用户密码");
        return cryptoUtil.hashPassword(rawPassword);
    }

    @Override
    public boolean verifyUserPassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            log.warn("密码验证参数不能为空");
            return false;
        }
        log.debug("正在验证用户密码");
        return cryptoUtil.verifyPassword(rawPassword, hashedPassword);
    }

    @Override
    public String encryptSensitiveData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return data;
        }
        log.debug("正在加密敏感数据");
        try {
            return cryptoUtil.encryptText(data);
        } catch (Exception e) {
            log.error("加密敏感数据失败", e);
            throw new RuntimeException("加密敏感数据失败", e);
        }
    }

    @Override
    public String decryptSensitiveData(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return encryptedData;
        }
        log.debug("正在解密敏感数据");
        try {
            return cryptoUtil.decryptText(encryptedData);
        } catch (Exception e) {
            log.warn("解密敏感数据失败，返回原始值", e);
            return encryptedData;
        }
    }

    @Override
    public String generateSecureToken() {
        log.debug("正在生成安全令牌");
        try {
            return cryptoUtil.generateSecureToken(32);
        } catch (Exception e) {
            log.error("生成安全令牌失败", e);
            throw new RuntimeException("生成安全令牌失败", e);
        }
    }

    @Override
    public String generateApiKey() {
        log.debug("正在生成API密钥");
        try {
            return cryptoUtil.generateSecureToken(64);
        } catch (Exception e) {
            log.error("生成API密钥失败", e);
            throw new RuntimeException("生成API密钥失败", e);
        }
    }

    @Override
    public String encryptFileData(String data, String customKey) {
        if (data == null || data.trim().isEmpty()) {
            return data;
        }
        if (customKey == null || customKey.trim().isEmpty()) {
            throw new IllegalArgumentException("自定义密钥不能为空");
        }
        log.debug("正在使用自定义密钥加密文件数据");
        try {
            String salt = cryptoUtil.generateSalt();
            String encrypted = cryptoUtil.encryptText(data, customKey, salt);
            return encrypted + ":" + salt;
        } catch (Exception e) {
            log.error("加密文件数据失败", e);
            throw new RuntimeException("加密文件数据失败", e);
        }
    }

    @Override
    public String decryptFileData(String encryptedData, String customKey) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return encryptedData;
        }
        if (customKey == null || customKey.trim().isEmpty()) {
            throw new IllegalArgumentException("自定义密钥不能为空");
        }
        log.debug("正在使用自定义密钥解密文件数据");
        try {
            String[] parts = encryptedData.split(":", 2);
            if (parts.length != 2) {
                log.warn("加密数据格式无效，返回原始值");
                return encryptedData;
            }
            return cryptoUtil.decryptText(parts[0], customKey, parts[1]);
        } catch (Exception e) {
            log.warn("解密文件数据失败，返回原始值", e);
            return encryptedData;
        }
    }

    @Override
    public String generateFileEncryptionKey() {
        log.debug("正在生成文件加密密钥");
        try {
            return cryptoUtil.generateAESKey();
        } catch (Exception e) {
            log.error("生成文件加密密钥失败", e);
            throw new RuntimeException("生成文件加密密钥失败", e);
        }
    }

    @Override
    public String encryptLargeData(String data, String aesKey) {
        if (data == null || data.trim().isEmpty()) {
            return data;
        }
        if (aesKey == null || aesKey.trim().isEmpty()) {
            throw new IllegalArgumentException("AES密钥不能为空");
        }
        log.debug("正在使用AES加密大型数据");
        try {
            return cryptoUtil.encryptAES(data, aesKey);
        } catch (Exception e) {
            log.error("AES加密大型数据失败", e);
            throw new RuntimeException("AES加密大型数据失败", e);
        }
    }

    @Override
    public String decryptLargeData(String encryptedData, String aesKey) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return encryptedData;
        }
        if (aesKey == null || aesKey.trim().isEmpty()) {
            throw new IllegalArgumentException("AES密钥不能为空");
        }
        log.debug("正在使用AES解密大型数据");
        try {
            return cryptoUtil.decryptAES(encryptedData, aesKey);
        } catch (Exception e) {
            log.warn("AES解密大型数据失败，返回原始值", e);
            return encryptedData;
        }
    }
}