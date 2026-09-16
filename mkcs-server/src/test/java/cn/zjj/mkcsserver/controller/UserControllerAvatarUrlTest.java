package cn.zjj.mkcsserver.controller;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.config.satoken.StpInterfaceImpl;
import cn.zjj.mkcsserver.service.UsersService;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.utils.MinIOUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerAvatarUrlTest {

    @Mock
    private UsersService usersService;

    @Mock
    private SaSession session;

    @Test
    void getUserInfoUsesCurrentMinioEndpointForLegacyAvatarUrls() {
        Users user = new Users();
        user.setId(1L);
        user.setUsername("avatar-user");
        user.setAvatarUrl("http://127.0.0.1:19000/avatar/avatar.jpg?avatarVersion=1789577557534");
        when(session.get(StpInterfaceImpl.SESSION_USER_KEY)).thenReturn(user);
        when(session.get(StpInterfaceImpl.SESSION_ROLE_KEY)).thenReturn(List.of());
        when(session.get(StpInterfaceImpl.SESSION_PERMISSION_KEY)).thenReturn(List.of());

        UserController controller = new UserController(
                usersService,
                new MinIOUtil("http://127.0.0.1:9000", "access-key", "secret-key", "default-bucket")
        );

        try (MockedStatic<StpUtil> stpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getSession).thenReturn(session);
            stpUtil.when(StpUtil::getTokenValue).thenReturn("token");
            stpUtil.when(StpUtil::getTokenTimeout).thenReturn(7200L);

            Result<LoginResponse> result = controller.getUserInfo();

            assertThat(result.getData().getAvatarUrl())
                    .isEqualTo("http://127.0.0.1:9000/avatar/avatar.jpg?avatarVersion=1789577557534");
        }
    }
}
