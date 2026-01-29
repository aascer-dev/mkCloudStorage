package cn.zjj.mkcsserver.config.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsserver.service.UserRolesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限认证接口实现
 * 
 * @author zjj
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StpInterfaceImpl implements StpInterface {

    private static final String SESSION_ROLE_LIST_KEY = "roleList";
    private static final String SESSION_PERMISSION_LIST_KEY = "permList";

    private final UserRolesService userRolesService;

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        try {
            SaSession accountSession = StpUtil.getSessionByLoginId(loginId);
            Object cached = accountSession.get(SESSION_PERMISSION_LIST_KEY);
            if (cached instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> cachedList = (List<String>) cached;
                return cachedList;
            }

            Long userId = Long.valueOf(loginId.toString());
            List<String> permissions = userRolesService.getUserPermissionNames(userId);
            accountSession.set(SESSION_PERMISSION_LIST_KEY, permissions);
            log.debug("用户 {} 的权限列表: {}", userId, permissions);
            return permissions;
        } catch (Exception e) {
            log.error("获取用户权限列表失败, loginId: {}, loginType: {}", loginId, loginType, e);
            return List.of();
        }
    }

    /**
     * 返回一个账号所拥有的角色标识集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            SaSession accountSession = StpUtil.getSessionByLoginId(loginId);
            Object cached = accountSession.get(SESSION_ROLE_LIST_KEY);
            if (cached instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> cachedList = (List<String>) cached;
                return cachedList;
            }

            Long userId = Long.valueOf(loginId.toString());
            List<String> roles = userRolesService.getUserRoleNames(userId);
            accountSession.set(SESSION_ROLE_LIST_KEY, roles);
            log.info("用户 {} 的角色列表: {}", userId, roles);
            return roles;
        } catch (Exception e) {
            log.error("获取用户角色列表失败, loginId: {}, loginType: {}", loginId, loginType, e);
            return List.of();
        }
    }
}