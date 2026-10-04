package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.dto.OAuth2UserInfo;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import com.zjj.mkcscommon.result.Result;

import java.util.Map;

/**
 * OAuth2 服务接口
 */
public interface OAuth2Service {
    
    /**
     * 获取 GitHub OAuth2 授权 URL
     * @param state 状态参数（用于防止 CSRF 攻击）
     * @return 授权 URL
     */
    String getGitHubAuthorizationUrl(String state);
    
    /**
     * 处理 GitHub OAuth2 回调
     * @param code 授权码
     * @param rememberMe 是否记住登录状态
     * @return 登录响应
     */
    Result<LoginResponse> handleGitHubCallback(String code, Boolean rememberMe);
    
    /**
     * 处理 GitHub OAuth2 回调（带 state 验证）
     * @param code 授权码
     * @param state state 参数
     * @param rememberMe 是否记住登录状态
     * @return 登录响应或注册要求
     */
    Result<?> handleGitHubCallback(String code, String state, Boolean rememberMe);
    
    /**
     * 通过授权码获取 GitHub 用户信息
     * @param code 授权码
     * @return GitHub 用户信息
     */
    OAuth2UserInfo getGitHubUserInfo(String code);
    
    /**
     * 发送邮箱验证码
     * @param tempUserId 临时用户ID
     * @param email 邮箱
     * @return 结果
     */
    Result<Void> sendVerificationCode(String tempUserId, String email);
    
    /**
     * 验证邮箱（使用验证码）
     * @param tempUserId 临时用户ID
     * @param email 邮箱
     * @param code 验证码
     * @return 结果
     */
    Result<Void> verifyEmailWithCode(String tempUserId, String email, String code);
    
    /**
     * 验证 GitHub 邮箱（无需验证码）
     * @param tempUserId 临时用户ID
     * @return 结果
     */
    Result<Void> verifyGitHubEmail(String tempUserId);
    
    /**
     * 完成 OAuth2 注册
     * @param tempUserId 临时用户ID
     * @param username 用户名
     * @param rememberMe 是否记住登录
     * @return 登录响应
     */
    Result<LoginResponse> completeOAuth2Registration(String tempUserId, String username, Boolean rememberMe);
    
    /**
     * 发起账号合并
     * @param tempUserId 临时用户ID
     * @param email 邮箱
     * @return 合并信息
     */
    Result<Map<String, Object>> initiateAccountMerge(String tempUserId, String email);
    
    /**
     * 完成账号合并
     * @param tempUserId 临时用户ID
     * @param existingUserId 现有用户ID
     * @param rememberMe 是否记住登录
     * @return 登录响应
     */
    Result<LoginResponse> completeAccountMerge(String tempUserId, Long existingUserId, Boolean rememberMe);


    /**
     * 解除 OAuth2 绑定
     * @param userId 用户ID
     * @param provider OAuth2 平台（如 github）
     * @return 结果
     */
    Result<Void> unbindOAuth(Long userId, String provider);

    /**
     * 获取用户的 OAuth2 绑定列表
     * @param userId 用户ID
     * @return OAuth2 绑定列表
     */
    Result<Map<String, Object>> getUserOAuthBindings(Long userId);

}
