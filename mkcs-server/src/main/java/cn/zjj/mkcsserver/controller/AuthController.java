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
}