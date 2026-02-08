package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.dto.SendVerificationCodeRequest;
import cn.zjj.mkcsmodel.dto.VerifyCodeRequest;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.AvailabilityResponse;
import cn.zjj.mkcsserver.converter.UserConverter;
import cn.zjj.mkcsserver.service.UsersService;
import cn.zjj.mkcsmodel.dto.LoginRequest;
import cn.zjj.mkcsmodel.dto.RegisterRequest;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.service.VerificationCodeService;
import cn.zjj.mkcsserver.service.OAuth2Service;
import cn.zjj.mkcsmodel.dto.OAuth2CallbackRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证管理", description = "用户认证相关接口")
@RequiredArgsConstructor
public class AuthController {

    private final UsersService usersService;
    private final VerificationCodeService verificationCodeService;
    private final OAuth2Service oauth2Service;
    
    @Value("${oauth2.frontend-callback-url:/oauth2-demo.html}")
    private String frontendCallbackUrl;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户并自动登录")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "注册成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "409", description = "用户名或邮箱已存在")
    })
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        return usersService.register(registerRequest);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "用户登录接口，支持记住我功能")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return usersService.login(loginRequest);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "用户登出接口，清除登录状态")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登出成功")
    })
    public com.zjj.mkcscommon.result.Result<String> logout() {
        StpUtil.checkLogin();
        StpUtil.logout();
        return Result.success("登出成功");
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/userinfo")
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<LoginResponse> getUserInfo() {
        // 检查登录状态
        StpUtil.checkLogin();

        // 构建用户信息响应
        Users users = usersService.getBaseMapper().selectById((String) StpUtil.getLoginId());
        return Result.success("获取用户信息成功", UserConverter.toLoginResponse(users, false));
    }

    /**
     * 检查登录状态
     */
    @GetMapping("/check")
    @Operation(summary = "检查登录状态", description = "检查当前用户的登录状态和Token信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "已登录"),
            @ApiResponse(responseCode = "2005", description = "用户未登录")
    }
    )
    public Result<Map<String, Object>> checkLogin() {

        //判断是否登录
        Assert.isTrue(StpUtil.isLogin(), ResultCode.NOT_LOGIN);

        return Result.success("用户已登录", null);
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "刷新当前用户的Token，延长有效期")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "刷新成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<Map<String, Object>> refreshToken() {
        // 检查登录状态
        Assert.isTrue(StpUtil.isLogin(), ResultCode.NOT_LOGIN);

        // 刷新Token（延长有效期）// 延长7天
        StpUtil.renewTimeout(7 * 24 * 60 * 60);

        Map<String, Object> data = new HashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("expiresIn", StpUtil.getTokenTimeout());

        return Result.success("Token刷新成功", data);
    }

    // 1. 检查用户名是否可用
    @GetMapping("/usernames/{username}/availability")
    @Operation(summary = "检查用户名是否可用（注册/编辑时使用）")
    public Result<AvailabilityResponse> checkUsernameAvailable(
            @PathVariable String username,
            @RequestParam(required = false) Long excludeUserId) {

        boolean available = usersService.isUsernameAvailable(username, excludeUserId);
        return Result.success(new AvailabilityResponse(available, available ? null : "用户名已被占用"));
    }

    // 2. 检查邮箱是否可用
    @GetMapping("/emails/{email}/availability")
    @Operation(summary = "检查邮箱是否可用（注册/编辑时使用）")
    public Result<AvailabilityResponse> checkEmailAvailable(
            @PathVariable String email,
            @RequestParam(required = false) Long excludeUserId) {

        boolean available = usersService.isEmailAvailable(email, excludeUserId);
        return Result.success(new AvailabilityResponse(available, available ? null : "邮箱已被占用"));
    }

    /**
     * 发送验证码
     */
    @PostMapping("/verification-code/send")
    @Operation(summary = "发送验证码", description = "发送邮箱验证码，支持注册、重置密码、登录验证等场景")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "发送成功"),
            @ApiResponse(responseCode = "400", description = "参数错误或发送过于频繁")
    })
    public Result<Map<String, Object>> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        verificationCodeService.sendVerificationCode(request);

        Map<String, Object> data = new HashMap<>();
        data.put("email", request.getEmail());
        data.put("type", request.getType());
        data.put("cooldown", 60); // 冷却时间（秒）
        data.put("ttl", 300); // 验证码有效期（秒）

        return Result.success("验证码发送成功，请查收邮件", data);
    }

    /**
     * 验证验证码
     */
    @PostMapping("/verification-code/verify")
    @Operation(summary = "验证验证码", description = "验证邮箱验证码是否正确")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "验证成功"),
            @ApiResponse(responseCode = "400", description = "验证码错误或已过期")
    })
    public Result<String> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        boolean verified = verificationCodeService.verifyCode(request);

        Assert.isTrue(verified, ResultCode.OPERATION_FAILED, "验证码错误或已过期");

        return Result.success("验证码验证成功");
    }

    /**
     * 检查验证码发送冷却状态
     */
    @GetMapping("/verification-code/cooldown")
    @Operation(summary = "检查验证码冷却状态", description = "查询指定邮箱和类型的验证码发送冷却时间")
    public Result<Map<String, Object>> checkCooldown(
            @RequestParam String email,
            @RequestParam String type) {

        long remainingSeconds = verificationCodeService.getRemainingCooldown(email, type);
        boolean canSend = remainingSeconds == 0;

        Map<String, Object> data = new HashMap<>();
        data.put("canSend", canSend);
        data.put("remainingSeconds", remainingSeconds);

        return Result.success(data);
    }

    // ==================== OAuth2 相关接口 ====================

    /**
     * 获取 GitHub OAuth2 授权 URL
     */
    @GetMapping("/oauth2/github/authorize")
    @Operation(summary = "获取 GitHub 授权 URL", description = "获取 GitHub OAuth2 授权链接，用于跳转到 GitHub 登录页面")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功")
    })
    public Result<Map<String, String>> getGitHubAuthUrl(@RequestParam(required = false) String state) {
        String authUrl = oauth2Service.getGitHubAuthorizationUrl(state);

        Map<String, String> data = new HashMap<>();
        data.put("authUrl", authUrl);

        return Result.success("获取授权链接成功", data);
    }

    /**
     * GitHub OAuth2 回调处理
     */
    @PostMapping("/oauth2/github/callback")
    @Operation(summary = "GitHub OAuth2 回调", description = "处理 GitHub OAuth2 授权回调，完成登录或注册")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功或需要注册"),
            @ApiResponse(responseCode = "400", description = "授权失败")
    })
    public Result<?> handleGitHubCallback(@Valid @RequestBody OAuth2CallbackRequest request) {
        return oauth2Service.handleGitHubCallback(request.getCode(), request.getState(), request.getRememberMe());
    }

    /**
     * GitHub OAuth2 回调处理（GET 方式，用于浏览器重定向）
     */
    @GetMapping("/oauth2/github/callback")
    @Operation(summary = "GitHub OAuth2 回调（GET）", description = "处理 GitHub OAuth2 授权回调（浏览器重定向方式）")
    public void handleGitHubCallbackGet(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false, defaultValue = "false") Boolean rememberMe,
            HttpServletResponse response) throws IOException {
        
        try {
            // 处理 GitHub 回调
            Result<?> result = oauth2Service.handleGitHubCallback(code, state, rememberMe);
            
            if (result.getCode() == 200) {
                Object data = result.getData();
                
                // Check if it's a login response or registration required
                if (data instanceof LoginResponse) {
                    LoginResponse loginResponse = (LoginResponse) data;
                    
                    // 构建重定向 URL，将 token 和用户信息传递给前端
                    String redirectUrl = String.format(
                        "%s?token=%s&userId=%d&username=%s&success=true",
                        frontendCallbackUrl,
                        loginResponse.getToken(),
                        loginResponse.getId(),
                        loginResponse.getUsername()
                    );
                    
                    log.info("GitHub OAuth2 登录成功，重定向到前端: {}", redirectUrl);
                    response.sendRedirect(redirectUrl);
                } else if (data instanceof Map) {
                    // Registration required
                    @SuppressWarnings("unchecked")
                    Map<String, Object> regData = (Map<String, Object>) data;
                    
                    String redirectUrl = String.format(
                        "%s?requiresRegistration=true&tempUserId=%s&suggestedEmail=%s&suggestedUsername=%s&hasGitHubEmail=%s",
                        frontendCallbackUrl,
                        regData.get("tempUserId"),
                        java.net.URLEncoder.encode(String.valueOf(regData.get("suggestedEmail")), "UTF-8"),
                        java.net.URLEncoder.encode(String.valueOf(regData.get("suggestedUsername")), "UTF-8"),
                        regData.get("hasGitHubEmail")
                    );
                    
                    log.info("GitHub OAuth2 新用户，需要注册，重定向到前端: {}", redirectUrl);
                    response.sendRedirect(redirectUrl);
                }
            } else {
                // 登录失败，重定向到前端错误页面
                String errorUrl = String.format(
                    "%s?success=false&error=%s",
                    frontendCallbackUrl,
                    java.net.URLEncoder.encode(result.getMessage(), "UTF-8")
                );
                
                log.error("GitHub OAuth2 登录失败: {}", result.getMessage());
                response.sendRedirect(errorUrl);
            }
        } catch (Exception e) {
            log.error("GitHub OAuth2 回调处理失败", e);
            
            // 异常情况，重定向到前端错误页面
            String errorUrl = String.format(
                "%s?success=false&error=%s",
                frontendCallbackUrl,
                java.net.URLEncoder.encode("登录失败: " + e.getMessage(), "UTF-8")
            );
            
            response.sendRedirect(errorUrl);
        }
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/oauth2/send-verification-code")
    @Operation(summary = "发送邮箱验证码", description = "为 OAuth2 注册发送邮箱验证码")
    public Result<Void> sendVerificationCode(
            @RequestParam String tempUserId,
            @RequestParam String email) {
        return oauth2Service.sendVerificationCode(tempUserId, email);
    }

    /**
     * 验证邮箱
     */
    @PostMapping("/oauth2/verify-email")
    @Operation(summary = "验证邮箱", description = "验证 OAuth2 注册邮箱（GitHub 邮箱或自定义邮箱）")
    public Result<Void> verifyEmail(
            @RequestParam String tempUserId,
            @RequestParam String email,
            @RequestParam(required = false) String code,
            @RequestParam(required = false, defaultValue = "false") Boolean isGitHubEmail) {
        
        if (Boolean.TRUE.equals(isGitHubEmail)) {
            // GitHub email - no code needed
            return oauth2Service.verifyGitHubEmail(tempUserId);
        } else {
            // Custom email - requires code
            return oauth2Service.verifyEmailWithCode(tempUserId, email, code);
        }
    }

    /**
     * 完成 OAuth2 注册
     */
    @PostMapping("/oauth2/complete-registration")
    @Operation(summary = "完成 OAuth2 注册", description = "完成 OAuth2 用户注册（用户名选择）")
    public Result<LoginResponse> completeRegistration(
            @RequestParam String tempUserId,
            @RequestParam String username,
            @RequestParam(required = false, defaultValue = "false") Boolean rememberMe) {
        return oauth2Service.completeOAuth2Registration(tempUserId, username, rememberMe);
    }

    /**
     * 发起账号合并
     */
    @PostMapping("/oauth2/initiate-merge")
    @Operation(summary = "发起账号合并", description = "检测邮箱冲突并发起账号合并流程")
    public Result<Map<String, Object>> initiateMerge(
            @RequestParam String tempUserId,
            @RequestParam String email) {
        return oauth2Service.initiateAccountMerge(tempUserId, email);
    }

    /**
     * 完成账号合并
     */
    @PostMapping("/oauth2/complete-merge")
    @Operation(summary = "完成账号合并", description = "在用户通过账号恢复验证后完成账号合并")
    public Result<LoginResponse> completeMerge(
            @RequestParam String tempUserId,
            @RequestParam Long existingUserId,
            @RequestParam(required = false, defaultValue = "false") Boolean rememberMe) {
        return oauth2Service.completeAccountMerge(tempUserId, existingUserId, rememberMe);
    }
    
    /**
     * 获取用户的 OAuth2 绑定列表
     */
    @GetMapping("/oauth2/bindings")
    @Operation(summary = "获取 OAuth2 绑定列表", description = "获取当前用户的所有 OAuth2 绑定")
    public Result<Map<String, Object>> getOAuthBindings() {
        StpUtil.checkLogin();
        Long userId = Long.parseLong((String) StpUtil.getLoginId());
        return oauth2Service.getUserOAuthBindings(userId);
    }
    
    /**
     * 解除 OAuth2 绑定
     */
    @DeleteMapping("/oauth2/bindings/{provider}")
    @Operation(summary = "解除 OAuth2 绑定", description = "解除指定平台的 OAuth2 绑定")
    public Result<Void> unbindOAuth(@PathVariable String provider) {
        StpUtil.checkLogin();
        Long userId = Long.parseLong((String) StpUtil.getLoginId());
        return oauth2Service.unbindOAuth(userId, provider);
    }
}
