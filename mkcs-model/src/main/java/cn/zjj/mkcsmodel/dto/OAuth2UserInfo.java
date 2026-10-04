package cn.zjj.mkcsmodel.dto;

import lombok.Data;

/**
 * OAuth2 用户信息
 */
@Data
public class OAuth2UserInfo {
    
    /**
     * 第三方平台的唯一标识
     */
    private String identifier;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
    
    /**
     * 访问令牌
     */
    private String accessToken;
}
