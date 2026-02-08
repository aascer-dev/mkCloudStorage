package cn.zjj.mkcsserver.config.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsserver.service.UserRolesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限认证接口实现
 * 修改版：优先从 SaSession 读取，实现高性能鉴权
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StpInterfaceImpl implements StpInterface {

    private final UserRolesService userRolesService;

    // 定义在 Session 中存储的 key 常量
    public static final String SESSION_PERMISSION_KEY = "USER_PERMISSIONS";
    public static final String SESSION_ROLE_KEY = "USER_ROLES";

    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionList(Object loginId, String loginType) {
        try {
            // 1. 获取当前账号的 Session (如果未登录，这里可能会抛出异常或返回null，视配置而定)
            // false 表示如果 Session 不存在，不自动创建，直接返回 null
            SaSession session = StpUtil.getSessionByLoginId(loginId, false);

            // 2. 尝试从 Session 中获取权限列表
            if (session != null) {
                Object cachedPermissions = session.get(SESSION_PERMISSION_KEY);
                if (cachedPermissions != null) {
                    // 这里的强制转换依赖于反序列化是否正确还原了 List 类型
                    return (List<String>) cachedPermissions;
                }
            }

            // 3. Session 中没有，查询数据库
            Long userId = Long.valueOf(loginId.toString());
            List<String> permissions = userRolesService.getUserPermissionNames(userId);
            if (permissions == null) {
                permissions = new ArrayList<>();
            }

            // 4. 将查询结果写入 Session，下次直接读取
            if (session != null) {
                session.set(SESSION_PERMISSION_KEY, permissions);
            }

            log.debug("用户 {} 权限列表加载完成 (来源: DB)", userId);
            return permissions;

        } catch (Exception e) {
            log.error("获取用户权限列表失败, loginId: {}", loginId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 返回一个账号所拥有的角色标识集合
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            SaSession session = StpUtil.getSessionByLoginId(loginId, false);

            if (session != null) {
                Object cachedRoles = session.get(SESSION_ROLE_KEY);
                if (cachedRoles != null) {
                    return (List<String>) cachedRoles;
                }
            }

            Long userId = Long.valueOf(loginId.toString());
            List<String> roles = userRolesService.getUserRoleNames(userId);
            if (roles == null) {
                roles = new ArrayList<>();
            }

            if (session != null) {
                session.set(SESSION_ROLE_KEY, roles);
            }

            log.debug("用户 {} 角色列表加载完成 (来源: DB)", userId);
            return roles;
        } catch (Exception e) {
            log.error("获取用户角色列表失败, loginId: {}", loginId, e);
            return new ArrayList<>();
        }
    }
}