package cn.zjj.mkcsserver;

import cn.zjj.mkcsmodel.vo.UserRolePermissionVO;
import cn.zjj.mkcsserver.service.UserRolesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * RBAC权限管理测试类
 * 
 * @author zjj
 */
@SpringBootTest(classes = MkcsServerApplication.class)
@ActiveProfiles("dev")
public class RbacTest {

    @Autowired
    private UserRolesService userRolesService;

    @Test
    public void testGetUserRolePermissions() {
        // 测试查询用户角色权限信息
        Long userId = 2016438988250046465L;
        
        UserRolePermissionVO result = userRolesService.getUserRolePermissions(userId);
        
        if (result != null) {
            System.out.println("用户ID: " + result.getUserId());
            System.out.println("用户名: " + result.getUsername());
            System.out.println("昵称: " + result.getNickname());
            
            System.out.println("角色列表:");
            if (result.getRoles() != null) {
                for (UserRolePermissionVO.RoleVO role : result.getRoles()) {
                    System.out.println("  - " + role.getRoleName() + ": " + role.getRoleDescription());
                    if (role.getPermissions() != null) {
                        for (UserRolePermissionVO.PermissionVO permission : role.getPermissions()) {
                            System.out.println("    * " + permission.getPermissionName() + ": " + permission.getPermissionDescription());
                        }
                    }
                }
            }
            
            System.out.println("所有权限:");
            if (result.getPermissions() != null) {
                for (UserRolePermissionVO.PermissionVO permission : result.getPermissions()) {
                    System.out.println("  - " + permission.getPermissionName() + ": " + permission.getPermissionDescription());
                }
            }
        } else {
            System.out.println("用户不存在或没有角色权限");
        }
    }

    @Test
    public void testGetUserRoleNames() {
        // 测试查询用户角色名称
        Long userId = 2016438988250046465L;
        
        List<String> roles = userRolesService.getUserRoleNames(userId);
        
        System.out.println("用户 " + userId + " 的角色:");
        for (String role : roles) {
            System.out.println("  - " + role);
        }
    }

    @Test
    public void testGetUserPermissionNames() {
        // 测试查询用户权限名称
        Long userId = 2016438988250046465L;
        
        List<String> permissions = userRolesService.getUserPermissionNames(userId);
        
        System.out.println("用户 " + userId + " 的权限:");
        for (String permission : permissions) {
            System.out.println("  - " + permission);
        }
    }

    @Test
    public void testHasRole() {
        // 测试检查用户是否拥有指定角色
        Long userId = 2016438988250046465L;
        String roleName = "ROLE_ADMIN";
        
        boolean hasRole = userRolesService.hasRole(userId, roleName);
        
        System.out.println("用户 " + userId + " 是否拥有角色 " + roleName + ": " + hasRole);
    }

    @Test
    public void testHasPermission() {
        // 测试检查用户是否拥有指定权限
        Long userId = 2016438988250046465L;
        String permissionName = "sys:user:create";
        
        boolean hasPermission = userRolesService.hasPermission(userId, permissionName);
        
        System.out.println("用户 " + userId + " 是否拥有权限 " + permissionName + ": " + hasPermission);
    }

    @Test
    public void testHasAllPermissions() {
        // 测试检查用户是否拥有所有指定权限
        Long userId = 2016438988250046465L;
        List<String> permissions = List.of("sys:user:create", "sys:user:update", "sys:user:delete");
        
        boolean hasAllPermissions = userRolesService.hasAllPermissions(userId, permissions);
        
        System.out.println("用户 " + userId + " 是否拥有所有权限 " + permissions + ": " + hasAllPermissions);
    }

    @Test
    public void testHasAnyPermission() {
        // 测试检查用户是否拥有任意指定权限
        Long userId = 2016438988250046465L;
        List<String> permissions = List.of("sys:user:create", "file:upload", "storage:manage");
        
        boolean hasAnyPermission = userRolesService.hasAnyPermission(userId, permissions);
        
        System.out.println("用户 " + userId + " 是否拥有任意权限 " + permissions + ": " + hasAnyPermission);
    }
}