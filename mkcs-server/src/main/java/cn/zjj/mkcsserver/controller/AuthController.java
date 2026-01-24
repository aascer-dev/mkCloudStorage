package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zjj.mkcscommon.Result;
import com.zjj.mkcscommon.ResultCode;
import cn.zjj.mkcsmodel.dto.LoginRequest;
import cn.zjj.mkcsmodel.dto.LoginResponse;
import cn.zjj.mkcsmodel.dto.UserInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 参数校验已通过@Valid注解完成
        
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
        
        return Result.success("登录成功", loginResponse);
    }
    
    /**
     * 登出接口
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        StpUtil.logout();
        return Result.success("登出成功");
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/userinfo")
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
        
        return Result.success("获取用户信息成功", userInfo);
    }
    
    /**
     * 检查登录状态
     */
    @GetMapping("/check")
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
        
        return Result.success("检查登录状态成功", data);
    }
    
    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshToken() {
        // 检查登录状态
        StpUtil.checkLogin();
        
        // 刷新Token（延长有效期）
        StpUtil.renewTimeout(2 * 60 * 60); // 延长2小时
        
        Map<String, Object> data = new HashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("expiresIn", StpUtil.getTokenTimeout());
        
        return Result.success("Token刷新成功", data);
    }

}