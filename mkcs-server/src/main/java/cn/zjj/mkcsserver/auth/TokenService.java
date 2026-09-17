package cn.zjj.mkcsserver.auth;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.mapper.UsersMapper;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final UsersMapper usersMapper;

    public TokenPair issueTokens(Users user) {
        Long tokenVersion = user.getTokenVersion() == null ? 1L : user.getTokenVersion();
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(user.getId(), user.getUsername(), tokenVersion);
        return new TokenPair(
                jwtProvider.issueAccessToken(authenticatedUser),
                refreshTokenService.create(authenticatedUser),
                jwtProvider.accessTokenTtlSeconds()
        );
    }

    public TokenPair refresh(String refreshToken) {
        RefreshTokenService.RotatedRefreshToken rotatedToken = refreshTokenService.rotate(refreshToken);
        AuthenticatedUser user = rotatedToken.user();
        Users currentUser = usersMapper.selectById(user.userId());
        if (currentUser == null || currentUser.getStatus() == null || currentUser.getStatus() != 1
                || !tokenVersionOf(currentUser).equals(user.tokenVersion())) {
            refreshTokenService.revoke(rotatedToken.refreshToken());
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return new TokenPair(jwtProvider.issueAccessToken(user), rotatedToken.refreshToken(), jwtProvider.accessTokenTtlSeconds());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private Long tokenVersionOf(Users user) {
        return user.getTokenVersion() == null ? 1L : user.getTokenVersion();
    }
}
