package cn.zjj.mkcsmodel.vo;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 当前默认使用的存储桶ID
     */
    private Long currentBucketId;
    
    /**
     * 唯一内部系统登录名/标识
     */
    private String username;
    
    /**
     * 显示昵称，允许重复
     */
    private String nickname;
    
    /**
     * 邮箱（可选，不唯一）
     */
    private String email;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
    
    /**
     * 用户状态（0: 禁用, 1: 正常）
     */
    private Byte status;
    
    /**
     * 用户角色列表
     */
    private List<String> roles;
    
    /**
     * 用户权限列表
     */
    private List<String> permissions;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}