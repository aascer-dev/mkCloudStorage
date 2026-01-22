package com.zjj.mkcs.pojo.dto;

import lombok.Data;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 用户更新请求DTO
 */
@Data
public class UpdateUserRequest {
    
    /**
     * 当前默认使用的存储桶ID
     */
    private Long currentBucketId;
    
    /**
     * 显示昵称，允许重复
     */
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
    
    /**
     * 邮箱（可选，不唯一）
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
    
    /**
     * 头像URL
     */
    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    private String avatarUrl;
}