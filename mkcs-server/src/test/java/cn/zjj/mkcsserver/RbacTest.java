package cn.zjj.mkcsserver;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.UserRolePermissionVO;
import cn.zjj.mkcsserver.mapper.UserRolesMapper;
import cn.zjj.mkcsserver.mapper.UsersMapper;
import cn.zjj.mkcsserver.service.impl.UserRolesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacTest {

    @Mock
    private UsersMapper usersMapper;

    @Mock
    private UserRolesMapper userRolesMapper;

    private UserRolesServiceImpl userRolesService;

    @BeforeEach
    void setUp() {
        userRolesService = new UserRolesServiceImpl(usersMapper);
        ReflectionTestUtils.setField(userRolesService, "baseMapper", userRolesMapper);
    }

    @Test
    void returnsNullWithoutQueryingWhenUserIdIsMissing() {
        assertThat(userRolesService.getUserRolePermissions(null)).isNull();

        verifyNoInteractions(usersMapper, userRolesMapper);
    }

    @Test
    void assemblesRolesAndDistinctPermissionsForExistingUser() {
        Users user = new Users();
        user.setUsername("rbac-user");
        user.setNickname("RBAC User");
        UserRolePermissionVO.RoleVO role = new UserRolePermissionVO.RoleVO();
        role.setRoleId(20L);
        role.setRoleName("ROLE_USER");
        UserRolePermissionVO.PermissionVO permission = new UserRolePermissionVO.PermissionVO();
        permission.setPermissionId(30L);
        permission.setPermissionName("file:read");

        when(usersMapper.selectById(10L)).thenReturn(user);
        when(userRolesMapper.selectUserRoles(10L)).thenReturn(List.of(role));
        when(userRolesMapper.selectRolePermissions(20L)).thenReturn(List.of(permission));
        when(userRolesMapper.selectUserPermissions(10L)).thenReturn(List.of(permission));

        UserRolePermissionVO result = userRolesService.getUserRolePermissions(10L);

        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getUsername()).isEqualTo("rbac-user");
        assertThat(result.getRoles()).singleElement().satisfies(actualRole ->
                assertThat(actualRole.getPermissions()).extracting(UserRolePermissionVO.PermissionVO::getPermissionName)
                        .containsExactly("file:read"));
        assertThat(result.getPermissions()).extracting(UserRolePermissionVO.PermissionVO::getPermissionName)
                .containsExactly("file:read");
    }

    @Test
    void rejectsInvalidAuthorizationArgumentsBeforeQuerying() {
        assertThat(userRolesService.hasRole(null, "ROLE_USER")).isFalse();
        assertThat(userRolesService.hasRole(10L, " ")).isFalse();
        assertThat(userRolesService.hasPermission(null, "file:read")).isFalse();
        assertThat(userRolesService.hasAllPermissions(10L, List.of())).isFalse();
        assertThat(userRolesService.hasAnyPermission(10L, List.of())).isFalse();

        verifyNoInteractions(userRolesMapper);
    }

    @Test
    void evaluatesRoleAndPermissionSetsUsingMapperResults() {
        when(userRolesMapper.countUserRole(10L, "ROLE_USER")).thenReturn(1);
        when(userRolesMapper.countUserPermission(10L, "file:read")).thenReturn(0);
        when(userRolesMapper.selectUserPermissionNames(10L)).thenReturn(List.of("file:read", "file:write"));

        assertThat(userRolesService.hasRole(10L, "ROLE_USER")).isTrue();
        assertThat(userRolesService.hasPermission(10L, "file:read")).isFalse();
        assertThat(userRolesService.hasAllPermissions(10L, List.of("file:read", "file:write"))).isTrue();
        assertThat(userRolesService.hasAllPermissions(10L, List.of("file:read", "admin:manage"))).isFalse();
        assertThat(userRolesService.hasAnyPermission(10L, List.of("admin:manage", "file:write"))).isTrue();
        assertThat(userRolesService.hasAnyPermission(10L, List.of("admin:manage"))).isFalse();

        verify(userRolesMapper).countUserRole(10L, "ROLE_USER");
        verify(userRolesMapper).countUserPermission(10L, "file:read");
    }
}
