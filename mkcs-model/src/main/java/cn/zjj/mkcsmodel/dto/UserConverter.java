package cn.zjj.mkcsmodel.dto;

import cn.zjj.mkcsmodel.entity.Users;

import java.util.List;

/**
 * 用户实体与DTO转换工具类
 */
public class UserConverter {
    
    /**
     * Users实体转LoginResponse
     */
    public static LoginResponse toLoginResponse(Users user, String token, Long expiresIn, 
                                              List<String> roles, List<String> permissions, 
                                              Boolean rememberMe) {
        if (user == null) {
            return null;
        }
        
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .id(user.getId())
                .currentBucketId(user.getCurrentBucketId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .permissions(permissions)
                .rememberMe(rememberMe)
                .build();
    }
    
    /**
     * Users实体转UserInfoResponse
     */
    public static UserInfoResponse toUserInfoResponse(Users user, List<String> roles, List<String> permissions) {
        if (user == null) {
            return null;
        }
        
        return UserInfoResponse.builder()
                .id(user.getId())
                .currentBucketId(user.getCurrentBucketId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .roles(roles)
                .permissions(permissions)
                .createdTime(user.getCreatedTime())
                .updateTime(user.getUpdateTime())
                .build();
    }
}