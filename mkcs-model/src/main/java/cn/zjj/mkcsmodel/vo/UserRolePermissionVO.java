package cn.zjj.mkcsmodel.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户角色权限信息响应VO
 *
 * @author zjj
 */
@Data
public class UserRolePermissionVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户角色列表
     */
    private List<RoleVO> roles;

    /**
     * 用户权限列表（去重后的所有权限）
     */
    private List<PermissionVO> permissions;

    /**
     * 角色信息VO
     */
    @Data
    public static class RoleVO {
        /**
         * 角色ID
         */
        private Long roleId;

        /**
         * 角色名称
         */
        private String roleName;

        /**
         * 角色描述
         */
        private String roleDescription;

        /**
         * 该角色拥有的权限列表
         */
        private List<PermissionVO> permissions;
    }

    /**
     * 权限信息VO
     */
    @Data
    public static class PermissionVO {
        /**
         * 权限ID
         */
        private Long permissionId;

        /**
         * 权限名称
         */
        private String permissionName;

        /**
         * 权限描述
         */
        private String permissionDescription;
    }
}