package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.UserRoles;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.UserRolePermissionVO;
import cn.zjj.mkcsserver.mapper.UserRolesMapper;
import cn.zjj.mkcsserver.mapper.UsersMapper;
import cn.zjj.mkcsserver.service.UserRolesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * <p>
 * 用户-角色关联表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserRolesServiceImpl extends ServiceImpl<UserRolesMapper, UserRoles> implements UserRolesService {

    private final UsersMapper usersMapper;

    @Override
    public boolean insertUserRoleRelation(UserRoles userRoles) {
        return save(userRoles);
    }

    @Override
    public UserRolePermissionVO getUserRolePermissions(Long userId) {
        if (userId == null) {
            return null;
        }

        // 查询用户基本信息
        Users user = usersMapper.selectById(userId);
        if (user == null) {
            log.warn("用户不存在: {}", userId);
            return null;
        }

        UserRolePermissionVO result = new UserRolePermissionVO();
        result.setUserId(userId);
        result.setUsername(user.getUsername());
        result.setNickname(user.getNickname());

        // 查询用户角色
        List<UserRolePermissionVO.RoleVO> roles = baseMapper.selectUserRoles(userId);
        result.setRoles(roles);

        // 为每个角色查询权限
        if (!CollectionUtils.isEmpty(roles)) {
            for (UserRolePermissionVO.RoleVO role : roles) {
                List<UserRolePermissionVO.PermissionVO> rolePermissions = 
                    baseMapper.selectRolePermissions(role.getRoleId());
                role.setPermissions(rolePermissions);
            }
        }

        // 查询用户的所有权限（去重）
        List<UserRolePermissionVO.PermissionVO> allPermissions = baseMapper.selectUserPermissions(userId);
        result.setPermissions(allPermissions);

        return result;
    }

    @Override
    public List<String> getUserRoleNames(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectUserRoleNames(userId);
    }

    @Override
    public List<String> getUserPermissionNames(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return baseMapper.selectUserPermissionNames(userId);
    }

    @Override
    public boolean hasRole(Long userId, String roleName) {
        if (userId == null || roleName == null || roleName.trim().isEmpty()) {
            return false;
        }
        return baseMapper.countUserRole(userId, roleName) > 0;
    }

    @Override
    public boolean hasPermission(Long userId, String permissionName) {
        if (userId == null || permissionName == null || permissionName.trim().isEmpty()) {
            return false;
        }
        return baseMapper.countUserPermission(userId, permissionName) > 0;
    }

    @Override
    public boolean hasAllPermissions(Long userId, List<String> permissionNames) {
        if (userId == null || CollectionUtils.isEmpty(permissionNames)) {
            return false;
        }

        // 获取用户的所有权限
        List<String> userPermissions = getUserPermissionNames(userId);
        if (CollectionUtils.isEmpty(userPermissions)) {
            return false;
        }

        // 检查是否包含所有指定权限
        return userPermissions.containsAll(permissionNames);
    }

    @Override
    public boolean hasAnyPermission(Long userId, List<String> permissionNames) {
        if (userId == null || CollectionUtils.isEmpty(permissionNames)) {
            return false;
        }

        // 获取用户的所有权限
        List<String> userPermissions = getUserPermissionNames(userId);
        if (CollectionUtils.isEmpty(userPermissions)) {
            return false;
        }

        // 检查是否包含任意一个指定权限
        return permissionNames.stream().anyMatch(userPermissions::contains);
    }
}
