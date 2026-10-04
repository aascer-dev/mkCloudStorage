package cn.zjj.mkcsserver.auth;

import cn.zjj.mkcsserver.service.UserRolesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRolesService userRolesService;

    public boolean hasPermission(String permission) {
        return userRolesService.hasPermission(UserContext.requireUserId(), permission);
    }

    public boolean hasRole(String role) {
        return userRolesService.hasRole(UserContext.requireUserId(), role);
    }

    public List<String> roles() {
        return userRolesService.getUserRoleNames(UserContext.requireUserId());
    }

    public List<String> permissions() {
        return userRolesService.getUserPermissionNames(UserContext.requireUserId());
    }

    public void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw new AccessDeniedException();
        }
    }

    public void requireRole(String role) {
        if (!hasRole(role)) {
            throw new AccessDeniedException();
        }
    }
}
