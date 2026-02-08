package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.dto.LoginRequest;
import cn.zjj.mkcsmodel.dto.RegisterRequest;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zjj.mkcscommon.result.Result;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户核心表 服务类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
public interface UsersService extends IService<Users> {

    /**
     * 用户登录
     * @param loginRequest 登录请求
     * @return 登录响应
     */
    Result<LoginResponse> login(LoginRequest loginRequest);

    /**
     * 用户注册（成功后可选择自动登录并返回登录响应）
     * @param registerRequest 注册请求
     * @return 登录响应
     */
    Result<LoginResponse> register(RegisterRequest registerRequest);

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    Users getUserByUsername(String username);

    /**
     * 根据邮箱查询用户
     * @param email 邮箱
     * @return 用户信息
     */
    Users getUserByEmail(String email);

    /**
     * 分页查询用户列表
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param username 用户名（模糊查询）
     * @param nickname 昵称（模糊查询）
     * @param email 邮箱（模糊查询）
     * @param status 状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分页结果
     */
    IPage<Users> getUserPage(int pageNum, int pageSize, 
                            String username, String nickname, 
                            String email, Byte status,
                            LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 关键词搜索用户
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param keyword 关键词
     * @return 分页结果
     */
    IPage<Users> searchUsers(int pageNum, int pageSize, String keyword);

    /**
     * 获取活跃用户列表
     * @return 活跃用户列表
     */
    List<Users> getActiveUsers();

    /**
     * 批量查询用户
     * @param userIds 用户ID列表
     * @return 用户列表
     */
    List<Users> getUsersByIds(List<Long> userIds);

    /**
     * 创建用户
     * @param user 用户信息
     * @return 创建的用户
     */
    Users createUser(Users user);

    /**
     * 更新用户信息
     * @param user 用户信息
     * @return 更新后的用户
     */
    Users updateUser(Users user);

    /**
     * 批量更新用户状态
     * @param userIds 用户ID列表
     * @param status 状态
     * @return 是否成功
     */
    boolean batchUpdateStatus(List<Long> userIds, Byte status);

    /**
     * 删除用户
     * @param userId 用户ID
     * @return 是否成功
     */
    boolean deleteUser(Long userId);


    /**
     * 获取最近注册的用户
     * @param limit 限制数量
     * @return 用户列表
     */
    List<Users> getRecentUsers(int limit);

    /**
     * 统计指定时间段的用户数量
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 用户数量
     */
    Long countUsersByDateRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 检查用户名是否可用
     * @param username 用户名
     * @param excludeUserId 排除的用户ID
     * @return 是否可用
     */
    boolean isUsernameAvailable(String username, Long excludeUserId);

    /**
     * 检查邮箱是否可用
     * @param email 邮箱
     * @param excludeUserId 排除的用户ID
     * @return 是否可用
     */
    boolean isEmailAvailable(String email, Long excludeUserId);

    /**
     * 更新用户信息（使用 DTO）
     * @param userId 用户ID
     * @param updateRequest 更新请求
     * @return 更新结果
     */
    Result<LoginResponse> updateUserInfo(Long userId, cn.zjj.mkcsmodel.dto.UpdateUserRequest updateRequest);

}
