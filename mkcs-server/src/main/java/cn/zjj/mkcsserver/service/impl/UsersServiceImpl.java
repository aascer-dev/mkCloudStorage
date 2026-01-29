package cn.zjj.mkcsserver.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.dto.LoginRequest;
import cn.zjj.mkcsmodel.dto.RegisterRequest;
import cn.zjj.mkcsmodel.entity.UserRoles;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.converter.UserConverter;
import cn.zjj.mkcsserver.mapper.UsersMapper;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UserRolesService;
import cn.zjj.mkcsserver.service.UsersService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.result.BusinessException;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户核心表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {

    private static final String SESSION_ROLE_LIST_KEY = "roleList";
    private static final String SESSION_PERMISSION_LIST_KEY = "permList";

    private final PasswordEncoder passwordEncoder;
    private final StorageBucketsService storageBucketsService;
    private final UserRolesService userRolesService;

    @Override
    public Result<LoginResponse> login(LoginRequest loginRequest) {
        // 参数校验已通过@Valid注解完成
        log.info("用户登录: username={}, rememberMe={}", loginRequest.getUsername(), loginRequest.getRememberMe());

        // 使用新的查询方法：支持用户名或邮箱登录
        Users user = baseMapper.selectByUsernameOrEmail(loginRequest.getUsername());
        Assert.notNull(user, ResultCode.USER_NOT_FOUND);

        // 验证用户状态
        Assert.isTrue(user.getStatus() == 1, ResultCode.USER_DISABLED);

        // 验证密码
        Assert.isTrue(passwordEncoder.matches(loginRequest.getPassword(), user.getPassword()),
                ResultCode.PASSWORD_ERROR);

        // 根据记住我设置不同的超时时间
        StpUtil.login(user.getId(), loginRequest.getRememberMe() ? 14 * 24 * 60 * 60 : 6 * 60 * 60);

        // 缓存角色/权限到 TokenSession
        cacheRolePermissionToSession(user.getId());

        // 构建登录响应
        LoginResponse loginResponse = UserConverter.toLoginResponse(user, loginRequest.getRememberMe());

        log.info("用户登录成功: userId={}", user.getId());
        return Result.success("登录成功", loginResponse);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResponse> register(RegisterRequest registerRequest) {
        // 基本参数校验由 @Valid 完成，这里进行业务校验
        Assert.hasText(registerRequest.getUsername(), "用户名不能为空");
        Assert.hasText(registerRequest.getPassword(), "密码不能为空");
        Assert.hasText(registerRequest.getEmail(), "邮箱不能为空");

        // 唯一性校验
        Assert.isTrue(!baseMapper.existsUsernameExcludeId(registerRequest.getUsername(), null), ResultCode.USER_EXISTS);
        Assert.isTrue(!baseMapper.existsEmailExcludeId(registerRequest.getEmail(), null), ResultCode.EMAIL_EXISTS);

        // 1) 创建用户（加密密码）
        Users user = new Users();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEmail(registerRequest.getEmail());
        user.setNickname(registerRequest.getNickname());
        user.setStatus((byte) 1);

        boolean saved = save(user);
        Assert.isTrue(saved, "用户注册失败");

        UserRoles userRoles = new UserRoles();
        userRoles.setUserId(user.getId());
        // 普通用户角色ID
        userRoles.setRoleId(2L);
        boolean savedRole = userRolesService.insertUserRoleRelation(userRoles);
        Assert.isTrue(savedRole, "用户角色分配失败");

        // 2) 创建默认存储桶（ownerId=用户ID），名称可用用户名或规则化名称
        String bucketName = ("user-" + user.getId()).toLowerCase();
        var bucket = storageBucketsService.createBucket(bucketName, "用户默认存储桶", user.getId());
        Assert.notNull(bucket, ResultCode.BUCKET_CREATE_FAILED);

        // 3) 更新用户的 currentBucketId
        user.setCurrentBucketId(bucket.getId());
        boolean updated = updateById(user);
        if (!updated) {
            throw new BusinessException(ResultCode.OPERATION_FAILED);
        }

        // 4) 自动登录并返回登录响应
        boolean rememberMe = Boolean.TRUE.equals(registerRequest.getRememberMe());
        StpUtil.login(user.getId(), rememberMe ? 14 * 24 * 60 * 60 : 6 * 60 * 60);

        // 缓存角色/权限到 TokenSession
        cacheRolePermissionToSession(user.getId());

        LoginResponse loginResponse = UserConverter.toLoginResponse(user, rememberMe);

        return Result.success("注册成功", loginResponse);
    }

    private void cacheRolePermissionToSession(Long userId) {
        if (userId == null) {
            return;
        }
        List<String> roles = userRolesService.getUserRoleNames(userId);
        List<String> permissions = userRolesService.getUserPermissionNames(userId);
        StpUtil.getSessionByLoginId(userId).set(SESSION_ROLE_LIST_KEY, roles);
        StpUtil.getSessionByLoginId(userId).set(SESSION_PERMISSION_LIST_KEY, permissions);
    }

    // ==================== 用户查询方法 ====================

    @Override
    public Users getUserByUsername(String username) {
        Assert.hasText(username, "用户名不能为空");
        return baseMapper.selectByUsername(username);
    }

    @Override
    public Users getUserByEmail(String email) {
        Assert.hasText(email, "邮箱不能为空");
        return baseMapper.selectByEmail(email);
    }

    @Override
    public IPage<Users> getUserPage(int pageNum, int pageSize,
                                    String username, String nickname,
                                    String email, Byte status,
                                    LocalDateTime startTime, LocalDateTime endTime) {
        Assert.isTrue(pageNum > 0, "页码必须大于0");
        Assert.isTrue(pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");

        Page<Users> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectUserPage(page, username, nickname, email, status, startTime, endTime);
    }

    @Override
    public IPage<Users> searchUsers(int pageNum, int pageSize, String keyword) {
        Assert.isTrue(pageNum > 0, "页码必须大于0");
        Assert.isTrue(pageSize > 0 && pageSize <= 100, "每页大小必须在1-100之间");

        Page<Users> page = new Page<>(pageNum, pageSize);
        return baseMapper.selectUserPageByKeyword(page, keyword);
    }

    @Override
    public List<Users> getActiveUsers() {
        return baseMapper.selectActiveUsers();
    }

    @Override
    public List<Users> getUsersByIds(List<Long> userIds) {
        Assert.notEmpty(userIds, "用户ID列表不能为空");
        return baseMapper.selectByIds(userIds);
    }

    // ==================== 用户管理方法 ====================

    @Override
    public Users createUser(Users user) {
        Assert.notNull(user, "用户信息不能为空");
        Assert.hasText(user.getUsername(), "用户名不能为空");
        Assert.hasText(user.getPassword(), "密码不能为空");

        // 检查用户名是否已存在
        Assert.isTrue(!baseMapper.existsUsernameExcludeId(user.getUsername(), null),
                ResultCode.USER_EXISTS);

        // 检查邮箱是否已存在（如果提供了邮箱）
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            Assert.isTrue(!baseMapper.existsEmailExcludeId(user.getEmail(), null),
                    ResultCode.EMAIL_EXISTS);
        }

        // 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 设置默认状态
        if (user.getStatus() == null) {
            user.setStatus((byte) 1);
        }

        // 保存用户
        boolean saved = save(user);
        Assert.isTrue(saved, "用户创建失败");

        log.info("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Override
    public Users updateUser(Users user) {
        Assert.notNull(user, "用户信息不能为空");
        Assert.notNull(user.getId(), "用户ID不能为空");

        // 检查用户是否存在
        Users existingUser = getById(user.getId());
        Assert.notNull(existingUser, ResultCode.USER_NOT_FOUND);

        // 检查用户名唯一性（如果修改了用户名）
        if (user.getUsername() != null && !user.getUsername().equals(existingUser.getUsername())) {
            Assert.isTrue(!baseMapper.existsUsernameExcludeId(user.getUsername(), user.getId()),
                    ResultCode.USER_EXISTS);
        }

        // 检查邮箱唯一性（如果修改了邮箱）
        if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
            Assert.isTrue(!baseMapper.existsEmailExcludeId(user.getEmail(), user.getId()),
                    ResultCode.EMAIL_EXISTS);
        }

        // 如果修改了密码，需要加密
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            // 不修改密码时，清空密码字段避免被更新
            user.setPassword(null);
        }

        // 更新用户
        boolean updated = updateById(user);
        Assert.isTrue(updated, "用户更新失败");

        log.info("用户更新成功: userId={}", user.getId());
        return getById(user.getId());
    }

    @Override
    public boolean batchUpdateStatus(List<Long> userIds, Byte status) {
        Assert.notEmpty(userIds, "用户ID列表不能为空");
        Assert.notNull(status, "状态不能为空");
        Assert.isTrue(status == 0 || status == 1, "状态值必须为0或1");

        int updated = baseMapper.updateStatusByIds(userIds, status);
        log.info("批量更新用户状态: userIds={}, status={}, updated={}", userIds, status, updated);
        return updated > 0;
    }

    @Override
    public boolean deleteUser(Long userId) {
        Assert.notNull(userId, "用户ID不能为空");

        Users user = getById(userId);
        Assert.notNull(user, ResultCode.USER_NOT_FOUND);

        boolean deleted = removeById(userId);
        if (deleted) {
            log.info("用户删除成功: userId={}, username={}", userId, user.getUsername());
        }
        return deleted;
    }

    // ==================== 统计查询方法 ====================

    @Override
    public List<Users> getRecentUsers(int limit) {
        Assert.isTrue(limit > 0 && limit <= 100, "限制数量必须在1-100之间");
        return baseMapper.selectRecentUsers(limit);
    }

    @Override
    public Long countUsersByDateRange(LocalDateTime startTime, LocalDateTime endTime) {
        return baseMapper.countUsersByDateRange(startTime, endTime);
    }

    @Override
    public boolean isUsernameAvailable(String username, Long excludeUserId) {
        Assert.hasText(username, "用户名不能为空");
        return !baseMapper.existsUsernameExcludeId(username, excludeUserId);
    }

    @Override
    public boolean isEmailAvailable(String email, Long excludeUserId) {
        Assert.hasText(email, "邮箱不能为空");
        return !baseMapper.existsEmailExcludeId(email, excludeUserId);
    }
}
