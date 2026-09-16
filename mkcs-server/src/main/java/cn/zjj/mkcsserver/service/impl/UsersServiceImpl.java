package cn.zjj.mkcsserver.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.dto.*;
import cn.zjj.mkcsmodel.entity.StorageBuckets;
import cn.zjj.mkcsmodel.entity.UserRoles;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.config.satoken.StpInterfaceImpl;
import cn.zjj.mkcsserver.converter.UserConverter;
import cn.zjj.mkcsserver.mapper.UsersMapper;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UserRolesService;
import cn.zjj.mkcsserver.service.UsersService;
import cn.zjj.mkcsserver.service.VerificationCodeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.utils.CommonUtils;
import com.zjj.mkcscommon.utils.CryptoUtil;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.enumeration.VerificationCodeType;
import com.zjj.mkcscommon.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private final CryptoUtil cryptoUtil;
    private final StorageBucketsService storageBucketsService;
    private final UserRolesService userRolesService;
    private final VerificationCodeService verificationCodeService;

    /**
     * 登录方法，支持用户名或邮箱登录，并根据记住我设置不同的超时时间
     *
     * @param loginRequest 登录请求
     * @return LoginResponse
     */
    @Override
    @Transactional(readOnly = true) // 1. 开启只读事务，确保所有查询复用同一个连接，解决 JDBC not managed 警告
    public Result<LoginResponse> login(LoginRequest loginRequest) {
        log.info("用户登录: username={}, rememberMe={}", loginRequest.getUsername(), loginRequest.getRememberMe());

        // 查询用户
        Users user = baseMapper.selectByUsernameOrEmail(loginRequest.getUsername());
        Assert.notNull(user, ResultCode.USER_NOT_FOUND);

        // 验证状态与密码
        Assert.isTrue(user.getStatus() == 1, ResultCode.USER_DISABLED);
        Assert.isTrue(cryptoUtil.verifyPassword(loginRequest.getPassword(), user.getPassword()), ResultCode.PASSWORD_ERROR);

        // 2. 执行登录（生成 Token）
        // 14天或6小时
        StpUtil.login(user.getId(), loginRequest.getRememberMe() ? 1209600 : 21600);

        // 3. 【关键优化】主动加载权限并存入 Session，防止 Converter 调用时 Session 尚未就绪导致击穿
        List<String> roles = userRolesService.getUserRoleNames(user.getId());
        List<String> permissions = userRolesService.getUserPermissionNames(user.getId());

        // 获取当前会话的 Session
        SaSession session = StpUtil.getSession();
        session.set(StpInterfaceImpl.SESSION_ROLE_KEY, roles);
        session.set(StpInterfaceImpl.SESSION_PERMISSION_KEY, permissions);
        
        // 【新增】将用户信息也存储到 Session 中
        session.set(StpInterfaceImpl.SESSION_USER_KEY, user);

        // 4. 将查好的数据传给 Converter，不再在 Converter 内部调用工具类

        log.info("用户登录成功: userId={}", user.getId());
        return Result.success("登录成功", setUserInfo(user));
    }

    /**
     * 注册方法，包含用户创建、默认存储桶创建、用户角色分配和自动登录等步骤
     *
     * @param registerRequest 注册请求
     * @return LoginResponse
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResponse> register(RegisterRequest registerRequest) {
        // 基本参数校验由 @Valid 完成，这里进行业务校验
        Assert.hasText(registerRequest.getUsername(), "用户名不能为空");
        Assert.hasText(registerRequest.getPassword(), "密码不能为空");
        Assert.hasText(registerRequest.getEmail(), "邮箱不能为空");

        // 唯一性校验交给createUser方法内部去实现

        // 1) 创建用户（密码加密由createUser方法处理）
        Users user = new Users();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(registerRequest.getPassword());
        user.setEmail(registerRequest.getEmail());
        user.setNickname(registerRequest.getNickname());
        user.setStatus((byte) 1);

        createUser(user);

        // 4) 自动登录并返回登录响应
        boolean rememberMe = Boolean.TRUE.equals(registerRequest.getRememberMe());
        StpUtil.login(user.getId(), rememberMe ? 14 * 24 * 60 * 60 : 6 * 60 * 60);

        // 主动加载用户角色和权限到Session
        SaSession session = StpUtil.getSession();
        List<String> roles = userRolesService.getUserRoleNames(user.getId());
        List<String> permissions = userRolesService.getUserPermissionNames(user.getId());
        session.set(StpInterfaceImpl.SESSION_ROLE_KEY, roles != null ? roles : new ArrayList<>());
        session.set(StpInterfaceImpl.SESSION_PERMISSION_KEY, permissions != null ? permissions : new ArrayList<>());
        session.set(StpInterfaceImpl.SESSION_USER_KEY, user);  // 存储用户信息

        return Result.success("注册成功", setUserInfo(user));
    }

    // ==================== 用户查询方法 ====================

    /**
     * 通过username查询用户
     *
     * @param username 用户名
     * @return User
     */
    @Override
    public Users getUserByUsername(String username) {
        Assert.hasText(username, "用户名不能为空");
        return baseMapper.selectByUsername(username);
    }

    /**
     * 邮箱查询用户
     *
     * @param email 邮箱
     * @return User
     */
    @Override
    public Users getUserByEmail(String email) {
        Assert.hasText(email, "邮箱不能为空");
        return baseMapper.selectByEmail(email);
    }

    /**
     * 分页查询用户列表，支持多条件过滤（用户名、昵称、邮箱、状态、注册时间范围）
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param username  用户名（模糊查询）
     * @param nickname  昵称（模糊查询）
     * @param email     邮箱（模糊查询）
     * @param status    状态
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return IPage<Users>
     */
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

    /**
     * 简化版分页查询，主要使用的MP自带的功能
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param keyword  关键词
     * @return IPage<Users>
     */
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

    /**
     * 创建新用户，以及设置对应的存储桶等
     *
     * @param user 用户信息
     * @return Users
     */
    @Override
    public Users createUser(Users user) {
        boolean saved;

        // 检查用户名是否已存在
        Assert.isTrue(isUsernameAvailable(user.getUsername(), null), ResultCode.USER_EXISTS);

        // 检查邮箱是否已存在（如果提供了邮箱）
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            Assert.isTrue(isEmailAvailable(user.getEmail(), null), ResultCode.EMAIL_EXISTS);
        }

        // 加密密码
        user.setPassword(cryptoUtil.hashPassword(user.getPassword()));

        // 设置默认状态
        if (user.getStatus() == null) {
            user.setStatus((byte) 1);
        }

        saved = save(user);
        Assert.isTrue(saved, "用户创建失败");

        UserRoles userRoles = new UserRoles();
        userRoles.setUserId(user.getId());
        // 普通用户角色ID
        userRoles.setRoleId(2L);
        boolean savedRole = userRolesService.insertUserRoleRelation(userRoles);
        Assert.isTrue(savedRole, "用户角色分配失败");

        // 2) 创建默认存储桶（ownerId=用户ID），名称使用UUID
        String bucketName = CommonUtils.generateUUID();
        StorageBuckets bucket = storageBucketsService.createBucket(bucketName, "用户默认存储桶", user.getId());
        Assert.notNull(bucket, ResultCode.BUCKET_CREATE_FAILED);

        // 3) 更新用户的 currentBucketId
        user.setCurrentBucketId(bucket.getId());
        boolean updated = updateById(user);
        Assert.isTrue(updated, ResultCode.OPERATION_FAILED);

        log.info("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    /**
     * 更新用户信息
     *
     * @param user 用户信息
     * @return Users
     */
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
            user.setPassword(cryptoUtil.hashPassword(user.getPassword()));
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
        Assert.isTrue(deleted, "用户删除失败，稍后再试");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<LoginResponse> updateUserInfo(Long userId, UpdateUserRequest updateRequest) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.notNull(updateRequest, "更新请求不能为空");

        // 1. 验证用户存在
        Users user = getById(userId);
        Assert.notNull(user, ResultCode.USER_NOT_FOUND);

        // 2. 验证邮箱唯一性（如果要更新邮箱）
        if (updateRequest.getEmail() != null && !updateRequest.getEmail().trim().isEmpty()) {
            String newEmail = updateRequest.getEmail().trim();
            // 如果邮箱有变化，检查是否已被其他用户使用
            if (!newEmail.equals(user.getEmail())) {
                if (!isEmailAvailable(newEmail, userId)) {
                    return Result.error(ResultCode.EMAIL_ALREADY_EXISTS);
                }
                user.setEmail(newEmail);
            }
        }

        // 3. 更新昵称
        if (updateRequest.getNickname() != null && !updateRequest.getNickname().trim().isEmpty()) {
            user.setNickname(updateRequest.getNickname().trim());
        }

        // 4. 更新头像URL
        if (updateRequest.getAvatarUrl() != null) {
            user.setAvatarUrl(updateRequest.getAvatarUrl());
        }

        // 5. 更新当前存储桶ID
        if (updateRequest.getCurrentBucketId() != null) {
            user.setCurrentBucketId(updateRequest.getCurrentBucketId());
        }

        // 6. 保存更新
        boolean success = updateById(user);
        Assert.isTrue(success, ResultCode.OPERATION_FAILED, "更新用户信息失败");

        // 7. 【关键】更新 Redis 中的 Session
        try {
            SaSession session = StpUtil.getSession();
            if (session != null) {
                // 重新加载权限和角色
                List<String> roles = userRolesService.getUserRoleNames(userId);
                List<String> permissions = userRolesService.getUserPermissionNames(userId);
                
                // 更新 Session 中的用户对象（包含最新的昵称、邮箱等）
                session.set(StpInterfaceImpl.SESSION_USER_KEY, user);
                
                // 更新 Session 中的权限和角色
                session.set(StpInterfaceImpl.SESSION_ROLE_KEY, roles);
                session.set(StpInterfaceImpl.SESSION_PERMISSION_KEY, permissions);
                
                // 【关键】更新 Session 到 Redis
                // 方式：直接修改 Session 对象，Sa-Token 会自动同步到 Redis
                // Session 对象是引用类型，修改后会自动保存
                log.info("Session 已更新: userId={}", userId);
            }
        } catch (Exception e) {
            log.warn("更新 Session 失败，但不影响业务逻辑: {}", e.getMessage());
        }

        // 8. 返回更新后的用户信息
        Users updatedUser = user;

        log.info("用户信息更新成功: userId={}, nickname={}, email={}",
                userId, updatedUser.getNickname(), updatedUser.getEmail());

        return Result.success("用户信息更新成功", setUserInfo(updatedUser));
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        Assert.isTrue(VerificationCodeType.RESET_PASSWORD.getCode().equals(request.getVerificationCodeType()),
                ResultCode.PARAM_INVALID, "验证码类型必须为 RESET_PASSWORD");

        // 验证存在该用户
        Users user = getUserByEmail(request.getEmail());
        Assert.notNull(user, ResultCode.USER_NOT_FOUND);

        // 验证表单中的密码重置验证码，验证成功后由验证码服务一次性消费。
        VerifyCodeRequest verifyCodeRequest = new VerifyCodeRequest(request.getEmail(), request.getCode(), request.getVerificationCodeType());
        Assert.isTrue(verificationCodeService.verifyCode(verifyCodeRequest), ResultCode.VERIFICATION_CODE_INVALID);

        // 更新密码并使该用户的已有登录会话失效，避免旧凭据继续使用。
        user.setPassword(cryptoUtil.hashPassword(request.getPassword()));

        Assert.isTrue(updateById(user), ResultCode.OPERATION_FAILED, "重置密码失败");
        StpUtil.kickout(user.getId());
        log.info("密码重置成功: userId={}", user.getId());
    }


    @Override
    public LoginResponse setUserInfo(Users user) {
        // 获取当前会话 Session
        SaSession session = StpUtil.getSession();

        // 取出角色列表
        List<String> roles = (List<String>) session.get(StpInterfaceImpl.SESSION_ROLE_KEY);

        // 取出权限列表
        List<String> permissions = (List<String>) session.get(StpInterfaceImpl.SESSION_PERMISSION_KEY);


        return UserConverter.toLoginResponse(user, roles, permissions);
    }
}


