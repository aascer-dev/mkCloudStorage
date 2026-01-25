package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.service.UsersService;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器 - 演示自动填充功能
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户相关操作，演示自动填充功能")
public class UserController {

    private final UsersService usersService;

    /**
     * 创建用户 - 演示插入时自动填充
     */
    @PostMapping
    @Operation(summary = "创建用户", description = "创建新用户，演示插入时自动填充创建时间、更新时间、创建人、更新人")
    public Result<Users> createUser(@RequestBody Users user) {
        log.info("创建用户: {}", user);
        
        // 保存用户，自动填充字段会被 MyMetaObjectHandler 处理
        boolean success = usersService.save(user);
        
        if (success) {
            return Result.success("用户创建成功", user);
        } else {
            return Result.error("用户创建失败");
        }
    }

    /**
     * 更新用户 - 演示更新时自动填充
     */
    @PutMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "更新用户", description = "更新用户信息，演示更新时自动填充更新时间、更新人")
    public Result<Users> updateUser(@PathVariable Long id, @RequestBody Users user) {
        log.info("更新用户 ID: {}, 数据: {}", id, user);
        
        user.setId(id);
        // 更新用户，自动填充字段会被 MyMetaObjectHandler 处理
        boolean success = usersService.updateById(user);
        
        if (success) {
            Users updatedUser = usersService.getById(id);
            return Result.success("用户更新成功", updatedUser);
        } else {
            return Result.error("用户更新失败");
        }
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    public Result<Users> getUserById(@PathVariable Long id) {
        Users user = usersService.getById(id);
        if (user != null) {
            return Result.success("获取成功", user);
        } else {
            return Result.error("用户不存在");
        }
    }
}