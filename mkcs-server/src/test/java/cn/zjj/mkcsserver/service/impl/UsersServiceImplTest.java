package cn.zjj.mkcsserver.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.dto.ResetPasswordRequest;
import cn.zjj.mkcsmodel.dto.VerifyCodeRequest;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsserver.service.UserRolesService;
import cn.zjj.mkcsserver.service.VerificationCodeService;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import com.zjj.mkcscommon.utils.CryptoUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
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

    @Test
    void resetPasswordUpdatesHashConsumesResetCodeAndKicksOutSessions() {
        Users user = new Users();
        user.setId(1001L);
        ResetPasswordRequest request = resetRequest();
        UsersServiceImpl usersService = spy(new UsersServiceImpl(
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService));
        doReturn(user).when(usersService).getUserByEmail(request.getEmail());
        doReturn(true).when(usersService).updateById(user);
        when(verificationCodeService.verifyCode(any(VerifyCodeRequest.class))).thenReturn(true);
        when(cryptoUtil.hashPassword(request.getPassword())).thenReturn("hashed-password");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            usersService.resetPassword(request);

            stpUtil.verify(() -> StpUtil.kickout(user.getId()));
        }

        assertThat(user.getPassword()).isEqualTo("hashed-password");
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
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService));
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
                cryptoUtil, storageBucketsService, userRolesService, verificationCodeService);

        assertThatThrownBy(() -> usersService.resetPassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.PARAM_INVALID.getCode());

        verifyNoInteractions(verificationCodeService, cryptoUtil);
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
