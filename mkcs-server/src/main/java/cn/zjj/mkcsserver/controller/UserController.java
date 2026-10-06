package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.auth.AuthorizationService;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsserver.service.UsersService;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.utils.CommonUtils;
import com.zjj.mkcscommon.utils.MinIOUtil;
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
    private final AuthorizationService authorizationService;

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
    @Operation(summary = "更新用户头像", description = "上传头像文件到MinIO的avatar桶，需要 user:updateAvatar 权限")
    public Result<Users> updateAvatar(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {

        log.info("更新用户头像 - 用户ID: {}, 文件大小: {} bytes, 内容类型: {}",
                id, file.getSize(), file.getContentType());

        // 1. 获取当前登录用户ID
        Long currentUserId = UserContext.requireUserId();

        // 2. 检查是否是本人
        boolean isSelf = currentUserId.equals(id);
        
        // 3. 权限检查
        if (isSelf) {
            // 更新自己的头像：需要 user:updateAvatar 权限
            boolean hasPermission = authorizationService.hasPermission("user:updateAvatar");
            log.info("用户 {} 更新自己的头像 - hasPermission: {}", currentUserId, hasPermission);
            
            if (!hasPermission) {
                log.warn("用户 {} 无 user:updateAvatar 权限", currentUserId);
                return Result.error("无权限更新头像，请联系管理员");
            }
        } else {
            // 更新他人的头像：需要管理员权限
            boolean isAdmin = authorizationService.hasRole("ROLE_ADMIN");
            boolean isSuperAdmin = authorizationService.hasRole("ROLE_SUPER_ADMIN");
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
            log.info("头像上传成功 - 用户ID: {}", id);
        } catch (Exception e) {
            log.error("上传头像到MinIO失败: {}", e.getMessage(), e);
            return Result.error("头像上传失败");
        }

        String oldAvatarObjectName = extractAvatarObjectName(user.getAvatarUrl());
        user.setAvatarUrl(avatarUrl);
        boolean success;
        try {
            success = usersService.updateById(user);
        } catch (RuntimeException exception) {
            cleanupUploadedAvatar(id, objectName);
            log.error("头像 URL 持久化异常，已尝试清理新对象: userId={}", id, exception);
            return Result.error("头像更新失败");
        }

        if (!success) {
            cleanupUploadedAvatar(id, objectName);
            log.warn("头像 URL 持久化失败，已尝试清理新对象: userId={}", id);
            return Result.error("头像更新失败");
        } else {
            if (oldAvatarObjectName != null) {
                boolean oldAvatarDeleted = minIOUtil.deleteObject(AVATAR_BUCKET, oldAvatarObjectName);
                if (!oldAvatarDeleted) {
                    log.warn("头像 URL 已更新，但旧对象删除失败: userId={}", id);
                }
            }

            Users updatedUser = usersService.getById(id);
            log.info("用户头像更新成功 - 用户ID: {}", id);
            return Result.success("头像更新成功", updatedUser);
        }
    }

    private void cleanupUploadedAvatar(Long userId, String objectName) {
        try {
            if (!minIOUtil.deleteObject(AVATAR_BUCKET, objectName)) {
                log.warn("新头像对象补偿清理失败: userId={}", userId);
            }
        } catch (RuntimeException exception) {
            log.error("新头像对象补偿清理异常: userId={}", userId, exception);
        }
    }

    private String extractAvatarObjectName(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }

        try {
            return minIOUtil.parseStoragePath(avatarUrl, AVATAR_BUCKET).objectKey();
        } catch (IllegalArgumentException exception) {
            log.warn("无法解析已存储的头像地址，跳过旧对象删除: userId={}", UserContext.get());
            return null;
        }
    }

    /**
     * 更新当前用户信息
     */
    @PutMapping("/profile")
    @Operation(summary = "更新当前用户信息", description = "更新当前登录用户的个人信息（昵称、邮箱等）")
    public Result<cn.zjj.mkcsmodel.vo.LoginResponse> updateProfile(
            @RequestBody @jakarta.validation.Valid cn.zjj.mkcsmodel.dto.UpdateUserRequest updateRequest) {
        
        // 获取当前登录用户ID
        Long currentUserId = UserContext.requireUserId();
        
        log.info("更新用户信息 - 用户ID: {}, 请求: {}", currentUserId, updateRequest);
        
        return usersService.updateUserInfo(currentUserId, updateRequest);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功"),
            @ApiResponse(responseCode = "401", description = "未登录")
    })
    public Result<LoginResponse> getUserInfo() {
        log.info("获取用户信息");
        
        Users user = usersService.getById(UserContext.requireUserId());
        if (user == null) {
            return Result.error("用户信息不存在");
        }
        
        log.info("用户信息获取成功 - 用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
        
        LoginResponse response = usersService.setUserInfo(user);
        response.setAvatarUrl(minIOUtil.normalizeBucketUrl(response.getAvatarUrl(), AVATAR_BUCKET));
        
        return Result.success("获取用户信息成功", response);
    }
}
