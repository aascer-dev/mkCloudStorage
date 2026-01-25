package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.enumeration.ResultCode;
import cn.zjj.mkcsmodel.dto.LoginRequest;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsmodel.vo.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User authentication related APIs")
public class AuthController {
    
    /**
     * User login
     */
    @PostMapping("/login")
    @Operation(summary = "User Login", description = "User login API with remember me functionality")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "401", description = "Invalid username or password")
    })
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 参数校验已通过@Valid注解完成
        log.info("用户登录: username={}, rememberMe={}", request.getUsername(), request.getRememberMe());
        // 这里应该是真实的用户验证逻辑
        // 为了演示，我们简单验证用户名密码
        if (!"admin".equals(request.getUsername()) || !"123456".equals(request.getPassword())) {
            return Result.error(ResultCode.LOGIN_FAILED);
        }
        
        // 根据remember me设置不同的超时时间
        long userId = 1001L; // 假设用户ID为1001
        
        if (Boolean.TRUE.equals(request.getRememberMe())) {
            // 记住我：7天有效期
            StpUtil.login(userId, 7 * 24 * 60 * 60);
        } else {
            // 普通登录：2小时有效期
            StpUtil.login(userId, 2 * 60 * 60);
        }

        // 构建登录响应
        LoginResponse loginResponse = LoginResponse.builder()
                .token(StpUtil.getTokenValue())
                .tokenType("Bearer")
                .expiresIn(StpUtil.getTokenTimeout())
                .id(userId)
                .currentBucketId(1L) // 示例存储桶ID
                .username(request.getUsername())
                .nickname("管理员")
                .email("admin@example.com")
                .avatarUrl("https://example.com/avatar.jpg")
                .status((byte) 1) // 正常状态
                .roles(Arrays.asList("ROLE_ADMIN", "ROLE_USER"))
                .permissions(Arrays.asList("file:read", "file:write", "file:delete", "sys:user:ban"))
                .rememberMe(request.getRememberMe())
                .build();
        
        return Result.success("Login successful", loginResponse);
    }
    
    /**
     * User logout
     */
    @PostMapping("/logout")
    @Operation(summary = "User Logout", description = "User logout API, clear login status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful")
    })
    public Result<String> logout() {
        StpUtil.logout();
        return Result.success("Logout successful");
    }
    
    /**
     * Get current user information
     */
    @GetMapping("/userinfo")
    @Operation(summary = "Get User Info", description = "获取当前登录用户信息，Get current logged-in user detailed information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Get successful"),
        @ApiResponse(responseCode = "401", description = "Not logged in")
    })
    public Result<UserInfoResponse> getUserInfo() {
        // 检查登录状态
        StpUtil.checkLogin();
        
        // 构建用户信息响应
        UserInfoResponse userInfo = UserInfoResponse.builder()
                .id(Long.valueOf(StpUtil.getLoginId().toString()))
                .currentBucketId(1L) // 示例存储桶ID
                .username("admin")
                .nickname("管理员")
                .email("admin@example.com")
                .avatarUrl("https://example.com/avatar.jpg")
                .status((byte) 1) // 正常状态
                .roles(StpUtil.getRoleList())
                .permissions(StpUtil.getPermissionList())
                .createdTime(LocalDateTime.now().minusDays(30))
                .updateTime(LocalDateTime.now())
                .build();
        
        return Result.success("Get user info successful", userInfo);
    }
    
    /**
     * Check login status
     */
    @GetMapping("/check")
    @Operation(summary = "检查登录状态", description = "Check current user login status and token information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Check successful")
    })
    public Result<Map<String, Object>> checkLogin() {
        Map<String, Object> data = new HashMap<>();
        data.put("isLogin", StpUtil.isLogin());
        
        if (StpUtil.isLogin()) {
            data.put("loginId", StpUtil.getLoginId());
            data.put("tokenValue", StpUtil.getTokenValue());
            data.put("tokenTimeout", StpUtil.getTokenTimeout());
            data.put("sessionTimeout", StpUtil.getSessionTimeout());
        } else {
            data.put("loginId", null);
            data.put("tokenValue", null);
            data.put("tokenTimeout", -1);
            data.put("sessionTimeout", -1);
        }
        
        return Result.success("Check login status successful", data);
    }
    
    /**
     * Refresh token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token", description = "Refresh current user token, extend validity period")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Refresh successful"),
        @ApiResponse(responseCode = "401", description = "Not logged in")
    })
    public Result<Map<String, Object>> refreshToken() {
        // 检查登录状态
        StpUtil.checkLogin();
        
        // 刷新Token（延长有效期）
        StpUtil.renewTimeout(2 * 60 * 60); // 延长2小时
        
        Map<String, Object> data = new HashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("expiresIn", StpUtil.getTokenTimeout());
        
        return Result.success("Token refresh successful", data);
    }

}