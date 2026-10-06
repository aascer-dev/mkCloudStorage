package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsserver.auth.AuthenticatedUser;
import cn.zjj.mkcsserver.auth.AuthorizationService;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.service.UsersService;
import com.zjj.mkcscommon.result.Result;
import com.zjj.mkcscommon.utils.MinIOUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerAvatarUploadTest {

    private static final String OLD_OBJECT_NAME = "old-avatar.png";
    private static final String OLD_AVATAR_URL = "http://minio.local/avatar/" + OLD_OBJECT_NAME + "?avatarVersion=1";
    private static final String NEW_AVATAR_URL = "http://minio.local/avatar/new-avatar.png";

    @Mock
    private UsersService usersService;

    @Mock
    private MinIOUtil minIOUtil;

    @Mock
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        when(minIOUtil.parseStoragePath(OLD_AVATAR_URL, "avatar"))
                .thenReturn(new MinIOUtil.ObjectLocation("avatar", OLD_OBJECT_NAME));
    }

    @Test
    void updateAvatarCleansUpNewObjectAndKeepsOldObjectWhenDatabaseUpdateFails() {
        Users user = userWithOldAvatar();
        when(usersService.getById(1L)).thenReturn(user);
        when(minIOUtil.bucketExists("avatar")).thenReturn(true);
        when(minIOUtil.upload(org.mockito.ArgumentMatchers.any(), eq("avatar"), anyString())).thenReturn(NEW_AVATAR_URL);
        when(usersService.updateById(user)).thenReturn(false);

        Result<Users> result = updateAvatar(user);

        assertThat(result.isSuccess()).isFalse();
        verify(minIOUtil).deleteObject(eq("avatar"), org.mockito.ArgumentMatchers.argThat(objectName -> !OLD_OBJECT_NAME.equals(objectName)));
        verify(minIOUtil, never()).deleteObject("avatar", OLD_OBJECT_NAME);
    }

    @Test
    void updateAvatarCleansUpNewObjectAndKeepsOldObjectWhenDatabaseUpdateThrows() {
        Users user = userWithOldAvatar();
        when(usersService.getById(1L)).thenReturn(user);
        when(minIOUtil.bucketExists("avatar")).thenReturn(true);
        when(minIOUtil.upload(org.mockito.ArgumentMatchers.any(), eq("avatar"), anyString())).thenReturn(NEW_AVATAR_URL);
        when(usersService.updateById(user)).thenThrow(new RuntimeException("database unavailable"));

        Result<Users> result = updateAvatar(user);

        assertThat(result.isSuccess()).isFalse();
        verify(minIOUtil).deleteObject(eq("avatar"), org.mockito.ArgumentMatchers.argThat(objectName -> !OLD_OBJECT_NAME.equals(objectName)));
        verify(minIOUtil, never()).deleteObject("avatar", OLD_OBJECT_NAME);
    }

    @Test
    void updateAvatarPersistsNewUrlBeforeDeletingOldObject() {
        Users user = userWithOldAvatar();
        Users updatedUser = userWithOldAvatar();
        updatedUser.setAvatarUrl(NEW_AVATAR_URL);
        when(usersService.getById(1L)).thenReturn(user, updatedUser);
        when(minIOUtil.bucketExists("avatar")).thenReturn(true);
        when(minIOUtil.upload(org.mockito.ArgumentMatchers.any(), eq("avatar"), anyString())).thenReturn(NEW_AVATAR_URL);
        when(usersService.updateById(user)).thenReturn(true);

        Result<Users> result = updateAvatar(user);

        ArgumentCaptor<Users> savedUser = ArgumentCaptor.forClass(Users.class);
        verify(usersService).updateById(savedUser.capture());
        assertThat(savedUser.getValue().getAvatarUrl()).isEqualTo(NEW_AVATAR_URL);
        InOrder persistedBeforeCleanup = inOrder(usersService, minIOUtil);
        persistedBeforeCleanup.verify(usersService).updateById(user);
        persistedBeforeCleanup.verify(minIOUtil).deleteObject("avatar", OLD_OBJECT_NAME);
        assertThat(result.getData().getAvatarUrl()).isEqualTo(NEW_AVATAR_URL);
    }

    private Result<Users> updateAvatar(Users user) {
        UserController controller = new UserController(usersService, minIOUtil, authorizationService);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        UserContext.set(new AuthenticatedUser(user.getId(), user.getUsername(), 1L));
        when(authorizationService.hasPermission("user:updateAvatar")).thenReturn(true);
        try {
            return controller.updateAvatar(user.getId(), file);
        } finally {
            UserContext.clear();
        }
    }

    private Users userWithOldAvatar() {
        Users user = new Users();
        user.setId(1L);
        user.setUsername("avatar-user");
        user.setAvatarUrl(OLD_AVATAR_URL);
        return user;
    }
}
