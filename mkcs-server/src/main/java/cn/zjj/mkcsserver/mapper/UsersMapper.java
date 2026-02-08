package cn.zjj.mkcsserver.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.zjj.mkcsmodel.entity.Users;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户核心表 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface UsersMapper extends BaseMapper<Users> {

    // ==================== 基础查询方法 ====================
    
    /**
     * 根据用户名查询用户（精确匹配）
     * 性能优化：username 字段应建立唯一索引
     */
    default Users selectByUsername(String username) {
        return selectOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getUsername, username)
                .last("LIMIT 1"));
    }

    /**
     * 根据邮箱查询用户
     * 性能优化：email 字段应建立索引
     */
    default Users selectByEmail(String email) {
        return selectOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getEmail, email)
                .last("LIMIT 1"));
    }

    /**
     * 根据用户名或邮箱查询用户（登录场景）
     */
    default Users selectByUsernameOrEmail(String loginName) {
        return selectOne(new LambdaQueryWrapper<Users>()
                .eq(Users::getUsername, loginName)
                .or()
                .eq(Users::getEmail, loginName)
                .last("LIMIT 1"));
    }

    /**
     * 查询活跃用户列表（状态为正常）
     */
    default List<Users> selectActiveUsers() {
        return selectList(new LambdaQueryWrapper<Users>()
                .eq(Users::getStatus, 1)
                .orderByDesc(Users::getCreatedAt));
    }

    /**
     * 根据状态查询用户数量
     */
    default Long countByStatus(Byte status) {
        return selectCount(new LambdaQueryWrapper<Users>()
                .eq(Users::getStatus, status));
    }

    // ==================== 分页查询方法 ====================

    /**
     * 分页查询用户列表（支持多条件筛选）
     * @param page 分页参数
     * @param username 用户名（模糊查询）
     * @param nickname 昵称（模糊查询）
     * @param email 邮箱（模糊查询）
     * @param status 状态（精确查询）
     * @param startTime 创建时间开始
     * @param endTime 创建时间结束
     */
    default IPage<Users> selectUserPage(Page<Users> page, 
                                       String username, 
                                       String nickname, 
                                       String email, 
                                       Byte status,
                                       LocalDateTime startTime, 
                                       LocalDateTime endTime) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<Users>()
                .like(username != null && !username.trim().isEmpty(), Users::getUsername, username)
                .like(nickname != null && !nickname.trim().isEmpty(), Users::getNickname, nickname)
                .like(email != null && !email.trim().isEmpty(), Users::getEmail, email)
                .eq(status != null, Users::getStatus, status)
                .ge(startTime != null, Users::getCreatedAt, startTime)
                .le(endTime != null, Users::getCreatedAt, endTime)
                .orderByDesc(Users::getCreatedAt);
        
        return selectPage(page, wrapper);
    }

    /**
     * 简化版分页查询（只支持关键词搜索）
     */
    default IPage<Users> selectUserPageByKeyword(Page<Users> page, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return selectPage(page, new LambdaQueryWrapper<Users>()
                    .orderByDesc(Users::getCreatedAt));
        }
        
        return selectPage(page, new LambdaQueryWrapper<Users>()
                .like(Users::getUsername, keyword)
                .or()
                .like(Users::getNickname, keyword)
                .or()
                .like(Users::getEmail, keyword)
                .orderByDesc(Users::getCreatedAt));
    }

    // ==================== 批量操作方法 ====================

    /**
     * 根据ID列表批量查询用户
     */
    default List<Users> selectByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<Users>()
                .in(Users::getId, userIds));
    }

    /**
     * 批量更新用户状态
     */
    default int updateStatusByIds(List<Long> userIds, Byte status) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        
        Users updateEntity = new Users();
        updateEntity.setStatus(status);
        
        return update(updateEntity, new LambdaQueryWrapper<Users>()
                .in(Users::getId, userIds));
    }

    // ==================== 统计查询方法 ====================

    /**
     * 查询最近注册的用户
     */
    default List<Users> selectRecentUsers(int limit) {
        return selectList(new LambdaQueryWrapper<Users>()
                .orderByDesc(Users::getCreatedAt)
                .last("LIMIT " + limit));
    }

    /**
     * 查询指定时间段内注册的用户数量
     */
    default Long countUsersByDateRange(LocalDateTime startTime, LocalDateTime endTime) {
        return selectCount(new LambdaQueryWrapper<Users>()
                .ge(startTime != null, Users::getCreatedAt, startTime)
                .le(endTime != null, Users::getCreatedAt, endTime));
    }

    // ==================== 复杂查询方法 ====================

    /**
     * 查询有头像的活跃用户
     */
    default List<Users> selectActiveUsersWithAvatar() {
        return selectList(new LambdaQueryWrapper<Users>()
                .eq(Users::getStatus, 1)
                .isNotNull(Users::getAvatarUrl)
                .ne(Users::getAvatarUrl, "")
                .orderByDesc(Users::getCreatedAt));
    }

    /**
     * 查询指定存储桶的用户列表
     */
    default List<Users> selectUsersByBucketId(Long bucketId) {
        return selectList(new LambdaQueryWrapper<Users>()
                .eq(Users::getCurrentBucketId, bucketId)
                .eq(Users::getStatus, 1));
    }

    /**
     * 检查用户名是否存在（排除指定用户ID）
     */
    default boolean existsUsernameExcludeId(String username, Long excludeId) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<Users>()
                .eq(Users::getUsername, username);
        
        if (excludeId != null) {
            wrapper.ne(Users::getId, excludeId);
        }
        
        return selectCount(wrapper) > 0;
    }

    /**
     * 检查邮箱是否存在（排除指定用户ID）
     */
    default boolean existsEmailExcludeId(String email, Long excludeId) {
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<Users>()
                .eq(Users::getEmail, email);
        
        if (excludeId != null) {
            wrapper.ne(Users::getId, excludeId);
        }
        
        return selectCount(wrapper) > 0;
    }
}
