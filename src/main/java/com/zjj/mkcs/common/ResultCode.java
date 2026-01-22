package com.zjj.mkcs.common;

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
    
    // 认证相关错误
    TOKEN_INVALID(2001, "Token无效"),
    TOKEN_EXPIRED(2002, "Token已过期"),
    LOGIN_FAILED(2003, "登录失败"),
    PERMISSION_DENIED(2004, "权限不足"),
    
    // 参数相关错误
    PARAM_MISSING(3001, "缺少必要参数"),
    PARAM_INVALID(3002, "参数格式错误"),
    PARAM_OUT_OF_RANGE(3003, "参数超出范围");
    
    /**
     * 状态码
     */
    private final Integer code;
    
    /**
     * 消息
     */
    private final String message;
}