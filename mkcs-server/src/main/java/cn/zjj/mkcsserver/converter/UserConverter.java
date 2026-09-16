package cn.zjj.mkcsserver.converter;

import cn.dev33.satoken.stp.StpUtil;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.vo.LoginResponse;

import java.util.List;

public class UserConverter {
    /**
     * 将Users实体转换为LoginResponse响应对象
     * 接收已查出的 roles 和 permissions，避免产生额外的数据库 IO
     */
    public static LoginResponse toLoginResponse(Users user, List<String> roles, List<String> permissions) {
        return LoginResponse.builder()
                .token(StpUtil.getTokenValue())
                .tokenType("Bearer")
                .expiresIn(StpUtil.getTokenTimeout())
                .id(user.getId())
                .currentBucketId(user.getCurrentBucketId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
