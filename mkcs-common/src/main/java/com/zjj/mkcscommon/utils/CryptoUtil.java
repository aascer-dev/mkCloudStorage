package com.zjj.mkcscommon.utils;

import com.zjj.mkcscommon.properties.CryptoProperties;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 使用 Spring Security Crypto 的加密工具类
 */
@RequiredArgsConstructor
@Slf4j
@Getter
public class CryptoUtil {

    private final PasswordEncoder passwordEncoder;
    private final CryptoProperties cryptoProperties;

    @PostConstruct
    public void init() {
        log.info(
            "初始化 CryptoUtil，BCrypt强度: {}",
            cryptoProperties.getBcryptStrength()
        );
    }

    /**
     * 使用 BCrypt 哈希密码
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
        return encryptText(
            plainText,
            cryptoProperties.getDefaultPassword(),
            cryptoProperties.getDefaultSalt()
        );
    }

    /**
     * 使用默认密钥解密文本
     */
    public String decryptText(String encryptedText) {
        return decryptText(
            encryptedText,
            cryptoProperties.getDefaultPassword(),
            cryptoProperties.getDefaultSalt()
        );
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
    public String decryptText(
        String encryptedText,
        String password,
        String salt
    ) {
        try {
            TextEncryptor encryptor = Encryptors.text(password, salt);
            return encryptor.decrypt(encryptedText);
        } catch (Exception e) {
            throw new RuntimeException("文本解密失败", e);
        }
    }

    /**
     * 生成 AES 密钥
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
     * 使用 AES 加密数据
     */
    public String encryptAES(String plainText, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            // 创建 AES 密钥规范
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

            // 初始化加密器
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

            // 执行加密操作
            byte[] encryptedBytes = cipher.doFinal(
                plainText.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /**
     * 使用 AES 解密数据
     */
    public String decryptAES(String encryptedText, String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            // 创建 AES 密钥规范
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

            // 初始化解密器
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            // 解码并执行解密操作
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
     *
     * 使用场景：
     * - OAuth2 的 state 参数（防止 CSRF 攻击）
     * - 会话令牌 (Session Token)
     * - API 密钥
     * - 重置密码的临时令牌
     *
     * 安全性说明：
     * - 使用 SecureRandom 而非普通 Random，提供加密级别的随机性
     * - 建议 length 至少为 32 字节，确保令牌难以被猜测或暴力破解
     *
     * @param length 随机字节数组的长度（注意：Base64 编码后的字符串长度会更长）
     * @return 返回 URL 安全的 Base64 编码字符串（无填充字符）
     *
     * 示例：
     * generateSecureToken(32) 可能返回 "xK9pLmN2qR5tY8zB3cD7fG1hJ4kL6mP0vWxYz"
     */
    public String generateSecureToken(int length) {
        // 1. 创建指定长度的字节数组，用于存储随机字节
        byte[] token = new byte[length];

        // 2. 使用 SecureRandom（加密安全的随机数生成器）生成随机字节
        //    SecureRandom 比普通的 Random 更安全，适合生成密码、令牌等敏感数据
        //    nextBytes() 方法会将生成的随机字节填充到 token 数组中
        new SecureRandom().nextBytes(token);

        // 3. 将随机字节数组编码为 Base64 字符串
        //    - getUrlEncoder(): 使用 URL 安全的 Base64 编码（用 - 和 _ 替代 + 和 /）
        //    - withoutPadding(): 去掉末尾的 = 填充字符，使令牌更简洁
        //    - encodeToString(): 将字节数组转换为字符串
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    /**
     * 生成临时密码（明文）
     *
     * 使用场景：
     * - 用户注册时的初始密码
     * - 忘记密码后生成的临时密码
     * - 管理员重置用户密码
     *
     * 密码组成规则：
     * - 包含大写字母（A-Z）
     * - 包含小写字母（a-z）
     * - 包含数字（0-9）
     * - 包含特殊字符（!@#$%^&*）
     * - 确保每种字符类型至少出现一次
     *
     * 安全性说明：
     * - 使用 SecureRandom 生成加密级别的随机密码
     * - 建议长度至少为 12 位
     * - 生成后应通过安全渠道（邮件/短信）发送给用户
     * - 建议用户首次登录后强制修改密码
     *
     * @param length 密码长度，建议至少 12 位
     * @return 返回生成的临时密码明文
     *
     * 示例：
     * generateTemporaryPassword(12) 可能返回 "Kx9@mP2!zB5q"
     */
    public String generateTemporaryPassword(int length) {
        // 验证密码长度，至少需要 4 位才能包含所有字符类型
        if (length < 4) {
            throw new IllegalArgumentException("密码长度至少需要 4 位");
        }

        // 定义字符集
        String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"; // 大写字母
        String lowerCase = "abcdefghijklmnopqrstuvwxyz"; // 小写字母
        String digits = "0123456789"; // 数字
        String specialChars = "!@#$%^&*"; // 特殊字符

        // 合并所有字符集
        String allChars = upperCase + lowerCase + digits + specialChars;

        // 使用 SecureRandom 生成密码
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(length);

        // 1. 确保每种字符类型至少出现一次（增强密码强度）
        password.append(upperCase.charAt(random.nextInt(upperCase.length())));
        password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(
            specialChars.charAt(random.nextInt(specialChars.length()))
        );

        // 2. 填充剩余长度的字符（从所有字符集中随机选择）
        for (int i = 4; i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // 3. 打乱密码字符顺序，避免固定的字符类型位置模式
        //    使用 Fisher-Yates 洗牌算法
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            // 交换 passwordArray[i] 和 passwordArray[j]
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    /**
     * 生成临时密码并加密
     *
     * 使用场景：
     * - 需要同时获取明文密码（发送给用户）和加密密码（存储到数据库）
     *
     * 工作流程：
     * 1. 生成临时密码明文
     * 2. 使用 BCrypt 加密密码
     * 3. 返回包含明文和密文的数组
     *
     * 安全性说明：
     * - 明文密码仅用于发送给用户，不应存储
     * - 加密密码使用 BCrypt 算法，存储到数据库
     * - BCrypt 是单向加密，无法解密，只能验证
     *
     * @param length 密码长度，建议至少 12 位
     * @return 返回 String 数组，[0]=明文密码，[1]=BCrypt 加密后的密码
     *
     * 示例：
     * String[] result = generateAndEncryptTemporaryPassword(12);
     * String plainPassword = result[0];  // "Kx9@mP2!zB5q" - 发送给用户
     * String encryptedPassword = result[1];  // "$2a$10$..." - 存储到数据库
     */
    public String[] generateAndEncryptTemporaryPassword(int length) {
        // 1. 生成临时密码明文
        String plainPassword = generateTemporaryPassword(length);

        // 2. 使用 BCrypt 加密密码
        //    BCrypt 是一种自适应哈希函数，专为密码存储设计
        //    特点：
        //    - 自动加盐（salt），每次加密同一密码结果都不同
        //    - 计算成本可配置（通过 bcryptStrength），防止暴力破解
        //    - 单向加密，不可逆，只能通过 matches() 验证
        String encryptedPassword = passwordEncoder.encode(plainPassword);

        // 3. 返回明文和密文
        //    返回数组而非对象，简化调用方使用
        //    [0] = 明文密码（用于发送给用户，例如通过邮件或短信）
        //    [1] = 加密密码（用于存储到数据库）
        return new String[] { plainPassword, encryptedPassword };
    }
}
