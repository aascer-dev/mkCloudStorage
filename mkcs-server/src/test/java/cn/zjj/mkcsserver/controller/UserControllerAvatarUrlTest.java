package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.auth.AuthenticatedUser;
import cn.zjj.mkcsserver.auth.AuthorizationService;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsserver.service.UsersService;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.utils.MinIOUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerAvatarUrlTest {

    @Mock
    private UsersService usersService;

    @Mock
    private AuthorizationService authorizationService;

    @Test
    void getUserInfoUsesPublicMinioEndpointForLegacyAvatarUrls() {
        Users user = new Users();
        user.setId(1L);
        user.setUsername("avatar-user");
        user.setAvatarUrl("http://minio:9000/avatar/avatar.jpg?avatarVersion=1789577557534");
        when(usersService.getById(1L)).thenReturn(user);
        when(usersService.setUserInfo(user)).thenReturn(LoginResponse.builder().avatarUrl(user.getAvatarUrl()).build());

        UserController controller = new UserController(
                usersService,
                new MinIOUtil("http://minio:9000", "https://files.example.com", "access-key", "secret-key", "default-bucket"),
                authorizationService
        );

        UserContext.set(new AuthenticatedUser(1L, "avatar-user", 1L));
        try {
            Result<LoginResponse> result = controller.getUserInfo();

            assertThat(result.getData().getAvatarUrl())
                    .isEqualTo("https://files.example.com/avatar/avatar.jpg?avatarVersion=1789577557534");
        } finally {
            UserContext.clear();
        }
    }
}
