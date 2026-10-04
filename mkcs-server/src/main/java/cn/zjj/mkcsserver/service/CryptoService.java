package cn.zjj.mkcsserver.service;

/**
 * 加密服务接口
 * 提供密码哈希、数据加密解密、令牌生成等功能
 */
public interface CryptoService {

    /**
     * 哈希用户密码
     * 
     * @param rawPassword 原始密码
     * @return 哈希后的密码
     */
    String hashUserPassword(String rawPassword);

    /**
     * 验证用户密码
     * 
     * @param rawPassword 原始密码
     * @param hashedPassword 哈希后的密码
     * @return 验证结果
     */
    boolean verifyUserPassword(String rawPassword, String hashedPassword);

    /**
     * 加密敏感数据（如邮箱、手机号等）
     * 
     * @param data 待加密的数据
     * @return 加密后的数据，如果输入为空则返回原值
     */
    String encryptSensitiveData(String data);

    /**
     * 解密敏感数据
     * 
     * @param encryptedData 加密的数据
     * @return 解密后的数据，解密失败时返回原值
     */
    String decryptSensitiveData(String encryptedData);

    /**
     * 生成安全令牌，用于密码重置、邮箱验证等
     * 
     * @return 安全令牌
     */
    String generateSecureToken();

    /**
     * 生成API密钥
     * 
     * @return API密钥
     */
    String generateApiKey();

    /**
     * 加密文件内容或元数据
     * 
     * @param data 待加密的数据
     * @param customKey 自定义密钥
     * @return 加密后的数据（包含盐值），如果输入为空则返回原值
     */
    String encryptFileData(String data, String customKey);

    /**
     * 解密文件内容或元数据
     * 
     * @param encryptedData 加密的数据（包含盐值）
     * @param customKey 自定义密钥
     * @return 解密后的数据，解密失败时返回原值
     */
    String decryptFileData(String encryptedData, String customKey);

    /**
     * 生成文件存储加密密钥
     * 
     * @return AES加密密钥（Base64编码）
     */
    String generateFileEncryptionKey();

    /**
     * 使用AES加密大型数据
     * 
     * @param data 待加密的数据
     * @param aesKey AES密钥（Base64编码）
     * @return 加密后的数据（Base64编码），如果输入为空则返回原值
     */
    String encryptLargeData(String data, String aesKey);

    /**
     * 使用AES解密大型数据
     * 
     * @param encryptedData 加密的数据（Base64编码）
     * @param aesKey AES密钥（Base64编码）
     * @return 解密后的数据，解密失败时返回原值
     */
    String decryptLargeData(String encryptedData, String aesKey);
}