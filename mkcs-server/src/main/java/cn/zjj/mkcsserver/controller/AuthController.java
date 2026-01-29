package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.converter.UserConverter;
import cn.zjj.mkcsserver.service.UsersService;
import cn.zjj.mkcsmodel.dto.LoginRequest;
import cn.zjj.mkcsmodel.dto.RegisterRequest;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsmodel.vo.UserInfoResponse;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDateTime;
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
        StpUtil.checkLogin();

        // 刷新Token（延长有效期）
        StpUtil.renewTimeout(7 * 24 * 60 * 60); // 延长7天

        Map<String, Object> data = new HashMap<>();
        data.put("token", StpUtil.getTokenValue());
        data.put("expiresIn", StpUtil.getTokenTimeout());

        return Result.success("Token刷新成功", data);
    }

    /**
     * 检查用户名/邮箱是否已存在
     */
    @GetMapping("/check-availability")
    @Operation(summary = "检查用户名或邮箱是否已存在", description = "注册前检测用户名/邮箱是否可用")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "检测成功"),
            @ApiResponse(responseCode = "400", description = "参数错误")
    })
    public Result<Map<String, Object>> checkAvailability(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email) {

        boolean hasUsername = username != null && !username.trim().isEmpty();
        boolean hasEmail = email != null && !email.trim().isEmpty();
        Assert.isTrue(hasUsername || hasEmail, "username 或 email 至少提供一个");

        Map<String, Object> data = new HashMap<>();
        if (hasUsername) {
            data.put("username", username);
            data.put("usernameAvailable", usersService.isUsernameAvailable(username, null));
        }
        if (hasEmail) {
            data.put("email", email);
            data.put("emailAvailable", usersService.isEmailAvailable(email, null));
        }
        return Result.success("检测成功", data);
    }

}