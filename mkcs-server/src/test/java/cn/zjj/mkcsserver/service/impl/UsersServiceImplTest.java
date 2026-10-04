package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsserver.auth.TokenService;
import cn.zjj.mkcsmodel.dto.ResetPasswordRequest;
import cn.zjj.mkcsmodel.dto.VerifyCodeRequest;
import cn.zjj.mkcsmodel.entity.Roles;
import cn.zjj.mkcsmodel.entity.StorageBuckets;
import cn.zjj.mkcsmodel.entity.UserRoles;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.mapper.RolesMapper;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UserRolesService;
import cn.zjj.mkcsserver.service.VerificationCodeService;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import com.zjj.mkcscommon.utils.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersServiceImplTest {

    @Mock
    private CryptoUtil cryptoUtil;

    @Mock
    private StorageBucketsService storageBucketsService;

    @Mock
    private UserRolesService userRolesService;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private RolesMapper rolesMapper;

    @Mock
    private TokenService tokenService;

    @Test
    void resetPasswordUpdatesHashConsumesResetCodeAndInvalidatesExistingTokens() {
        Users user = new Users();
        user.setId(1001L);
        user.setTokenVersion(1L);
        ResetPasswordRequest request = resetRequest();
        UsersServiceImpl usersService = spy(new UsersServiceImpl(
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService, rolesMapper, tokenService));
        doReturn(user).when(usersService).getUserByEmail(request.getEmail());
        doReturn(true).when(usersService).updateById(user);
        when(verificationCodeService.verifyCode(any(VerifyCodeRequest.class))).thenReturn(true);
        when(cryptoUtil.hashPassword(request.getPassword())).thenReturn("hashed-password");

        usersService.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("hashed-password");
        assertThat(user.getTokenVersion()).isEqualTo(2L);
        verify(verificationCodeService).verifyCode(argThat(codeRequest ->
                request.getEmail().equals(codeRequest.getEmail())
                        && request.getCode().equals(codeRequest.getCode())
                        && "RESET_PASSWORD".equals(codeRequest.getType())));
        verify(usersService).updateById(user);
    }

    @Test
    void resetPasswordDoesNotUpdatePasswordWhenVerificationFails() {
        Users user = new Users();
        ResetPasswordRequest request = resetRequest();
        UsersServiceImpl usersService = spy(new UsersServiceImpl(
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService, rolesMapper, tokenService));
        doReturn(user).when(usersService).getUserByEmail(request.getEmail());
        when(verificationCodeService.verifyCode(any(VerifyCodeRequest.class))).thenReturn(false);

        assertThatThrownBy(() -> usersService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.VERIFICATION_CODE_INVALID.getCode());

        verify(cryptoUtil, never()).hashPassword(any());
        verify(usersService, never()).updateById(any(Users.class));
    }

    @Test
    void resetPasswordRejectsNonResetVerificationCodeType() {
        ResetPasswordRequest request = resetRequest();
        request.setVerificationCodeType("REGISTER");
        UsersServiceImpl usersService = new UsersServiceImpl(
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService, rolesMapper, tokenService);

        assertThatThrownBy(() -> usersService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.PARAM_INVALID.getCode());

        verifyNoInteractions(verificationCodeService, cryptoUtil);
    }

    @Test
    void createUserAssignsTheRoleResolvedByName() {
        Users user = newUser();
        Roles defaultRole = new Roles();
        defaultRole.setId(9876L);
        defaultRole.setName("ROLE_USER");
        StorageBuckets bucket = new StorageBuckets();
        bucket.setId(2002L);

        UsersServiceImpl usersService = spy(new UsersServiceImpl(
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService, rolesMapper, tokenService));
        doReturn(true).when(usersService).isUsernameAvailable(user.getUsername(), null);
        doReturn(true).when(usersService).isEmailAvailable(user.getEmail(), null);
        doReturn(true).when(usersService).save(user);
        doReturn(true).when(usersService).updateById(user);
        when(cryptoUtil.hashPassword(user.getPassword())).thenReturn("hashed-password");
        when(rolesMapper.selectByName("ROLE_USER")).thenReturn(defaultRole);
        when(userRolesService.insertUserRoleRelation(any(UserRoles.class))).thenReturn(true);
        when(storageBucketsService.createBucket(any(), any(), any())).thenReturn(bucket);

        usersService.createUser(user);

        verify(userRolesService).insertUserRoleRelation(argThat(userRole ->
                user.getId().equals(userRole.getUserId())
                        && defaultRole.getId().equals(userRole.getRoleId())));
        assertThat(user.getCurrentBucketId()).isEqualTo(bucket.getId());
    }

    @Test
    void createUserFailsBeforeCreatingBucketWhenDefaultRoleIsMissing() {
        Users user = newUser();
        UsersServiceImpl usersService = spy(new UsersServiceImpl(
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService, rolesMapper, tokenService));
        doReturn(true).when(usersService).isUsernameAvailable(user.getUsername(), null);
        doReturn(true).when(usersService).isEmailAvailable(user.getEmail(), null);
        doReturn(true).when(usersService).save(user);
        when(cryptoUtil.hashPassword(user.getPassword())).thenReturn("hashed-password");

        assertThatThrownBy(() -> usersService.createUser(user))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认用户角色不存在");

        verifyNoInteractions(userRolesService, storageBucketsService);
    }

    private Users newUser() {
        Users user = new Users();
        user.setId(1001L);
        user.setUsername("new-user");
        user.setEmail("new-user@example.com");
        user.setPassword("plain-password");
        return user;
    }

    private ResetPasswordRequest resetRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@example.com");
        request.setCode("123456");
        request.setPassword("new-password");
        request.setVerificationCodeType("RESET_PASSWORD");
        return request;
    }
}
