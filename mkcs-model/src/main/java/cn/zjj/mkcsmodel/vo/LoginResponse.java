package cn.zjj.mkcsmodel.vo;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    /**
     * 访问令牌
     */
    private String token;
    
    /**
     * 令牌类型
     */
    @Builder.Default
    private String tokenType = "Bearer";
    
    /**
     * 令牌过期时间（秒）
     */
    private Long expiresIn;
    
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 当前默认使用的存储桶ID
     */
    private Long currentBucketId;
    
    /**
     * 唯一内部系统登录名/标识
     */
    private String username;
    
    /**
     * 显示昵称，允许重复
     */
    private String nickname;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
    
    /**
     * 用户状态（0: 禁用, 1: 正常）
     */
    private Byte status;
    
    /**
     * 用户角色列表
     */
    private List<String> roles;
    
    /**
     * 用户权限列表
     */
    private List<String> permissions;
    
    ///**
    // * 是否记住我
    // */
    //private Boolean rememberMe;
}