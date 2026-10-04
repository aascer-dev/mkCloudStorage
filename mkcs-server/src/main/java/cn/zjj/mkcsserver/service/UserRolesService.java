package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.UserRoles;
import cn.zjj.mkcsmodel.vo.UserRolePermissionVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用户-角色关联表 服务类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
public interface UserRolesService extends IService<UserRoles> {
    
    /**
     * 插入用户角色关联关系
     */
    boolean insertUserRoleRelation(UserRoles userRoles);

    /**
     * 根据用户ID查询用户的角色和权限信息
     *
     * @param userId 用户ID
     * @return 用户角色权限信息
     */
    UserRolePermissionVO getUserRolePermissions(Long userId);

    /**
     * 根据用户ID查询用户的所有角色名称
     *
     * @param userId 用户ID
     * @return 角色名称列表
     */
    List<String> getUserRoleNames(Long userId);

    /**
     * 根据用户ID查询用户的所有权限名称
     *
     * @param userId 用户ID
     * @return 权限名称列表
     */
    List<String> getUserPermissionNames(Long userId);

    /**
     * 检查用户是否拥有指定角色
     *
     * @param userId 用户ID
     * @param roleName 角色名称
     * @return 是否拥有该角色
     */
    boolean hasRole(Long userId, String roleName);

    /**
     * 检查用户是否拥有指定权限
     *
     * @param userId 用户ID
     * @param permissionName 权限名称
     * @return 是否拥有该权限
     */
    boolean hasPermission(Long userId, String permissionName);

    /**
     * 批量检查用户是否拥有指定权限
     *
     * @param userId 用户ID
     * @param permissionNames 权限名称列表
     * @return 是否拥有所有权限
     */
    boolean hasAllPermissions(Long userId, List<String> permissionNames);

    /**
     * 检查用户是否拥有任意一个指定权限
     *
     * @param userId 用户ID
     * @param permissionNames 权限名称列表
     * @return 是否拥有任意一个权限
     */
    boolean hasAnyPermission(Long userId, List<String> permissionNames);
}
