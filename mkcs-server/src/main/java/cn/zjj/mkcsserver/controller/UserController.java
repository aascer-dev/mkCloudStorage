package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.service.UsersService;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.utils.CommonUtils;
import com.zjj.mkcscommon.utils.MinIOUtil;
import com.zjj.mkcscommon.utils.RbacUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

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
    private final MinIOUtil minIOUtil;

    // avatar 桶名称常量
    private static final String AVATAR_BUCKET = "avatar";
    // 允许的图片类型
    private static final String[] ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"};
    // 最大文件大小：5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;


    /**
     * 更新用户头像 - 上传文件到MinIO
     */
    @PostMapping("/{id}/avatar")
    @SaCheckLogin
    @Operation(summary = "更新用户头像", description = "上传头像文件到MinIO的avatar桶，需要 user:updateAvatar 权限")
    public Result<Users> updateAvatar(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        log.info("更新用户头像 - 用户ID: {}, 文件名: {}, 文件大小: {} bytes",
                id, file.getOriginalFilename(), file.getSize());

        // 1. 获取当前登录用户ID
        Long currentUserId = StpUtil.getLoginIdAsLong();

        // 2. 检查是否是本人
        boolean isSelf = currentUserId.equals(id);
        
        // 3. 权限检查
        if (isSelf) {
            // 更新自己的头像：需要 user:updateAvatar 权限
            boolean hasPermission = RbacUtil.hasPermission("user:updateAvatar");
            log.info("用户 {} 更新自己的头像 - hasPermission: {}", currentUserId, hasPermission);
            
            if (!hasPermission) {
                log.warn("用户 {} 无 user:updateAvatar 权限", currentUserId);
                return Result.error("无权限更新头像，请联系管理员");
            }
        } else {
            // 更新他人的头像：需要管理员权限
            boolean isAdmin = RbacUtil.isAdmin();
            boolean isSuperAdmin = RbacUtil.isSuperAdmin();
            log.info("用户 {} 尝试更新用户 {} 的头像 - isAdmin: {}, isSuperAdmin: {}", 
                    currentUserId, id, isAdmin, isSuperAdmin);
            
            if (!isAdmin && !isSuperAdmin) {
                log.warn("用户 {} 不是管理员，无法更新他人头像", currentUserId);
                return Result.error("只有管理员可以更新他人头像");
            }
        }

        // 4. 验证用户是否存在
        Users user = usersService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 5. 验证文件
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        // 6. 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error("文件大小不能超过5MB");
        }

        // 7. 验证文件类型
        String contentType = file.getContentType();
        boolean isValidType = false;
        if (contentType != null) {
            for (String allowedType : ALLOWED_IMAGE_TYPES) {
                if (contentType.equalsIgnoreCase(allowedType)) {
                    isValidType = true;
                    break;
                }
            }
        }
        if (!isValidType) {
            return Result.error("只支持上传图片文件（JPEG、PNG、GIF、WebP）");
        }

        // 8. 确保avatar桶存在
        try {
            if (!minIOUtil.bucketExists(AVATAR_BUCKET)) {
                log.info("avatar桶不存在，正在创建...");
                boolean created = minIOUtil.createBucket(AVATAR_BUCKET);
                if (!created) {
                    log.error("创建avatar桶失败");
                    return Result.error("存储桶创建失败");
                }
                log.info("avatar桶创建成功");
            }
        } catch (Exception e) {
            log.error("检查或创建avatar桶时出错: {}", e.getMessage(), e);
            return Result.error("存储服务异常");
        }

        // 9. 生成唯一的对象名称
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectName = CommonUtils.generateUUID() + fileExtension;

        // 10. 上传文件到MinIO的avatar桶
        String avatarUrl;
        try {
            avatarUrl = minIOUtil.upload(file, AVATAR_BUCKET, objectName);
            log.info("头像上传成功 - 用户ID: {}, 对象名: {}, URL: {}", id, objectName, avatarUrl);
        } catch (Exception e) {
            log.error("上传头像到MinIO失败: {}", e.getMessage(), e);
            return Result.error("头像上传失败");
        }

        // 11. 删除旧头像（可选）
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            try {
                // 从URL中提取对象名称
                String oldAvatarUrl = user.getAvatarUrl();
                if (oldAvatarUrl.contains(AVATAR_BUCKET + "/")) {
                    String oldObjectName = oldAvatarUrl.substring(
                            oldAvatarUrl.indexOf(AVATAR_BUCKET + "/") + AVATAR_BUCKET.length() + 1);
                    minIOUtil.deleteObject(AVATAR_BUCKET, oldObjectName);
                    log.info("旧头像已删除: {}", oldObjectName);
                }
            } catch (Exception e) {
                log.warn("删除旧头像失败（不影响新头像上传）: {}", e.getMessage());
            }
        }

        // 12. 更新用户头像URL
        user.setAvatarUrl(avatarUrl);
        boolean success = usersService.updateById(user);

        if (success) {
            Users updatedUser = usersService.getById(id);
            log.info("用户头像更新成功 - 用户ID: {}", id);
            return Result.success("头像更新成功", updatedUser);
        } else {
            return Result.error("头像更新失败");
        }
    }

    /**
     * 更新当前用户信息
     */
    @PutMapping("/profile")
    @SaCheckLogin
    @Operation(summary = "更新当前用户信息", description = "更新当前登录用户的个人信息（昵称、邮箱等）")
    public Result<cn.zjj.mkcsmodel.vo.LoginResponse> updateProfile(
            @RequestBody @jakarta.validation.Valid cn.zjj.mkcsmodel.dto.UpdateUserRequest updateRequest) {
        
        // 获取当前登录用户ID
        Long currentUserId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        
        log.info("更新用户信息 - 用户ID: {}, 请求: {}", currentUserId, updateRequest);
        
        return usersService.updateUserInfo(currentUserId, updateRequest);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    @SaCheckLogin
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<LoginResponse> getUserInfo() {
        log.info("获取用户信息");
        
        // 从 Redis Session 中获取所有信息（不查询数据库）
        SaSession session = StpUtil.getSession();
        
        // 获取用户信息
        Users user = (Users) session.get(cn.zjj.mkcsserver.config.satoken.StpInterfaceImpl.SESSION_USER_KEY);
        if (user == null) {
            log.warn("Session 中用户信息为空，从数据库读取");
            user = usersService.getById(StpUtil.getLoginIdAsLong());
            if (user == null) {
                return Result.error("用户信息不存在");
            }
            // 将用户信息存入 Session
            session.set(cn.zjj.mkcsserver.config.satoken.StpInterfaceImpl.SESSION_USER_KEY, user);
        }
        
        // 获取角色列表
        List<String> roles = (List<String>) session.get(cn.zjj.mkcsserver.config.satoken.StpInterfaceImpl.SESSION_ROLE_KEY);
        
        // 获取权限列表
        List<String> permissions = (List<String>) session.get(cn.zjj.mkcsserver.config.satoken.StpInterfaceImpl.SESSION_PERMISSION_KEY);
        
        log.info("用户信息获取成功 - 用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        // 构建登录响应（完全从 Redis 读取）
        LoginResponse response = cn.zjj.mkcsserver.converter.UserConverter.toLoginResponse(user, roles, permissions);
        response.setAvatarUrl(minIOUtil.normalizeBucketUrl(response.getAvatarUrl(), AVATAR_BUCKET));
        
        return Result.success("获取用户信息成功", response);
    }
}
