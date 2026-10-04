package com.zjj.mkcscommon.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 返回结果码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    
    // 成功
    SUCCESS(200, "操作成功"),
    
    // 客户端错误
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    VALIDATION_ERROR(422, "参数校验失败"),
    
    // 服务器错误
    ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    
    // 业务错误
    BUSINESS_ERROR(1000, "业务处理失败"),
    DATA_NOT_FOUND(1001, "数据不存在"),
    DATA_ALREADY_EXISTS(1002, "数据已存在"),
    OPERATION_FAILED(1003, "操作失败"),
    
    // 用户相关错误
    USER_NOT_FOUND(1100, "用户不存在"),
    USER_EXISTS(1101, "用户已存在"),
    USER_DISABLED(1102, "用户已被禁用"),
    PASSWORD_ERROR(1103, "密码错误"),
    EMAIL_EXISTS(1104, "邮箱已存在"),
    USERNAME_ALREADY_EXISTS(1105, "用户名已存在"),
    EMAIL_ALREADY_EXISTS(1106, "邮箱已被使用"),
    
    // 认证相关错误
    TOKEN_INVALID(2001, "Token无效"),
    TOKEN_EXPIRED(2002, "Token已过期"),
    LOGIN_FAILED(2003, "登录失败"),
    PERMISSION_DENIED(2004, "权限不足"),
    NOT_LOGIN(2005, "用户未登录"),
    
    // OAuth2 相关错误
    OAUTH_STATE_INVALID(2101, "OAuth2 state 参数无效"),
    OAUTH_TEMP_DATA_EXPIRED(2102, "OAuth2 临时数据已过期，请重新登录"),
    OAUTH_ALREADY_LINKED(2103, "该 OAuth 账号已绑定到其他用户"),
    EMAIL_FORMAT_INVALID(2104, "邮箱格式不正确"),
    EMAIL_NOT_VERIFIED(2105, "邮箱未验证"),
    EMAIL_NOT_PROVIDED(2106, "GitHub 未提供邮箱"),
    VERIFICATION_CODE_EXPIRED(2107, "验证码已过期"),
    VERIFICATION_CODE_INVALID(2108, "验证码不正确"),
    EMAIL_SEND_FAILED(2109, "验证码发送失败"),
    
    // 参数相关错误
    PARAM_MISSING(3001, "缺少必要参数"),
    PARAM_INVALID(3002, "参数格式错误"),
    PARAM_OUT_OF_RANGE(3003, "参数超出范围"),
    
    // 存储桶相关错误
    BUCKET_NOT_FOUND(4001, "存储桶不存在"),
    BUCKET_NAME_EXISTS(4002, "存储桶名称已存在"),
    BUCKET_CREATE_FAILED(4003, "存储桶创建失败"),
    BUCKET_DELETE_FAILED(4004, "存储桶删除失败"),
    BUCKET_UPDATE_FAILED(4005, "存储桶更新失败"),
    INVALID_BUCKET_NAME(4006, "存储桶名称格式不正确"),
    DEFAULT_BUCKET_CANNOT_DELETE(4007, "默认存储桶不能删除"),
    ACCESS_DENIED(4008, "访问被拒绝"),
    STORAGE_QUOTA_EXCEEDED(4009, "存储空间不足");
    
    /**
     * 状态码
     */
    private final Integer code;
    
    /**
     * 消息
     */
    private final String message;
}
