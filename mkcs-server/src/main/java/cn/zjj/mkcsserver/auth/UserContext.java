package cn.zjj.mkcsserver.auth;

import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;

public final class UserContext {

    private static final ThreadLocal<AuthenticatedUser> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthenticatedUser user) {
        CURRENT_USER.set(user);
    }

    public static Long requireUserId() {
        AuthenticatedUser user = CURRENT_USER.get();
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user.userId();
    }

    public static AuthenticatedUser get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
