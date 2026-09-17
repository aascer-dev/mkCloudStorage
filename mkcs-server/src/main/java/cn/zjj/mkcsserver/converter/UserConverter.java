package cn.zjj.mkcsserver.converter;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;
import cn.zjj.mkcsserver.auth.TokenPair;

import java.util.List;

public class UserConverter {
    /**
     * 将Users实体转换为LoginResponse响应对象
     * 接收已查出的 roles 和 permissions，避免产生额外的数据库 IO
     */
    public static LoginResponse toLoginResponse(Users user, List<String> roles, List<String> permissions, TokenPair tokenPair) {
        LoginResponse.LoginResponseBuilder builder = LoginResponse.builder()
                .tokenType("Bearer")
                .id(user.getId())
                .currentBucketId(user.getCurrentBucketId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .permissions(permissions);
        if (tokenPair != null) {
            builder.accessToken(tokenPair.accessToken())
                    .refreshToken(tokenPair.refreshToken())
                    .expiresIn(tokenPair.expiresIn());
        }
        return builder.build();
    }
}
