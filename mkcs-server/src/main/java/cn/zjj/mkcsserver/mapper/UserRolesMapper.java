package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.UserRoles;
import cn.zjj.mkcsmodel.vo.UserRolePermissionVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户-角色关联表 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface UserRolesMapper extends BaseMapper<UserRoles> {

    /**
     * 根据用户ID查询用户的角色信息
     *
     * @param userId 用户ID
     * @return 角色信息列表
     */
    List<UserRolePermissionVO.RoleVO> selectUserRoles(@Param("userId") Long userId);

    /**
     * 根据用户ID查询用户的所有权限信息（去重）
     *
     * @param userId 用户ID
     * @return 权限信息列表
     */
    List<UserRolePermissionVO.PermissionVO> selectUserPermissions(@Param("userId") Long userId);

    /**
     * 根据角色ID查询角色的权限信息
     *
     * @param roleId 角色ID
     * @return 权限信息列表
     */
    List<UserRolePermissionVO.PermissionVO> selectRolePermissions(@Param("roleId") Long roleId);

    /**
     * 根据用户ID查询用户的所有角色名称
     *
     * @param userId 用户ID
     * @return 角色名称列表
     */
    List<String> selectUserRoleNames(@Param("userId") Long userId);

    /**
     * 根据用户ID查询用户的所有权限名称
     *
     * @param userId 用户ID
     * @return 权限名称列表
     */
    List<String> selectUserPermissionNames(@Param("userId") Long userId);

    /**
     * 检查用户是否拥有指定角色
     *
     * @param userId 用户ID
     * @param roleName 角色名称
     * @return 记录数量
     */
    int countUserRole(@Param("userId") Long userId, @Param("roleName") String roleName);

    /**
     * 检查用户是否拥有指定权限
     *
     * @param userId 用户ID
     * @param permissionName 权限名称
     * @return 记录数量
     */
    int countUserPermission(@Param("userId") Long userId, @Param("permissionName") String permissionName);
}
