package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.BusinessException;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 示例控制器 - 演示通用返回结果和异常处理的使用
 */
@Slf4j
@RestController
@RequestMapping("/api/example")
@Tag(name = "示例接口", description = "演示各种功能的示例接口")
public class ExampleController {
    
    /**
     * 成功返回示例
     */
    @GetMapping("/success")
    @Operation(summary = "成功返回示例", description = "演示正常的成功返回结果")
    public Result<String> success() {
        return Result.success("操作成功", "这是返回的数据");
    }
    
    /**
     * 业务异常示例
     */
    @GetMapping("/business-error")
    @Operation(summary = "业务异常示例", description = "演示业务异常的处理")
    public Result<Void> businessError() {
        throw new BusinessException("这是一个业务异常");
    }
    
    /**
     * 使用Assert工具类示例
     */
    @GetMapping("/assert-error")
    @Operation(summary = "Assert工具类示例", description = "演示Assert工具类的使用")
    public Result<Void> assertError(
            @Parameter(description = "姓名参数", required = true) 
            @RequestParam String name) {
        Assert.notEmpty(name, "姓名不能为空");
        Assert.isTrue(name.length() > 2, "姓名长度必须大于2");
        return Result.success();
    }
    
    /**
     * 参数校验异常示例
     */
    @PostMapping("/validation-error")
    @Operation(summary = "参数校验示例", description = "演示参数校验异常的处理")
    public Result<String> validationError(@Valid @RequestBody UserRequest request) {
        return Result.success("用户信息", request.getName());
    }
    
    /**
     * 系统异常示例
     */
    @GetMapping("/system-error")
    @Operation(summary = "系统异常示例", description = "演示系统异常的处理")
    public Result<Void> systemError() {
        // 故意制造空指针异常
        String str = null;
        str.length();
        return Result.success();
    }
    
    /**
     * 需要登录的接口示例
     */
    @SaCheckLogin
    @GetMapping("/need-login")
    @Operation(summary = "需要登录的接口", description = "演示需要登录才能访问的接口")
    public Result<String> needLogin() {
        return Result.success("登录验证通过", "当前用户ID: " + StpUtil.getLoginId());
    }
    
    /**
     * 需要角色的接口示例
     */
    @SaCheckRole("admin")
    @GetMapping("/need-role")
    @Operation(summary = "需要角色的接口", description = "演示需要admin角色才能访问的接口")
    public Result<String> needRole() {
        return Result.success("角色验证通过", "当前用户拥有admin角色");
    }
    
    /**
     * 需要权限的接口示例
     */
    @SaCheckPermission("user:delete")
    @GetMapping("/need-permission")
    @Operation(summary = "需要权限的接口", description = "演示需要user:delete权限才能访问的接口")
    public Result<String> needPermission() {
        return Result.success("权限验证通过", "当前用户拥有user:delete权限");
    }
    
    /**
     * 用户请求DTO
     */
    @Tag(name = "UserRequest", description = "用户请求对象")
    public static class UserRequest {
        @NotBlank(message = "姓名不能为空")
        @Parameter(description = "用户姓名", required = true)
        private String name;
        
        @NotNull(message = "年龄不能为空")
        @Parameter(description = "用户年龄", required = true)
        private Integer age;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
    }
}