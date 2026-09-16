package com.zjj.mkcscommon.enumeration;

import lombok.Getter;

/**
 * 验证码类型枚举
 */
@Getter
public enum VerificationCodeType {
    
    REGISTER("REGISTER", "注册验证"),
    RESET_PASSWORD("RESET_PASSWORD", "重置密码验证"),
    LOGIN("LOGIN", "登录验证"),
    CHANGE_EMAIL("CHANGE_EMAIL", "修改邮箱验证"),
    CHANGE_PASSWORD("CHANGE_PASSWORD", "修改账号密码");

    private final String code;
    private final String description;

    VerificationCodeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static VerificationCodeType fromCode(String code) {
        for (VerificationCodeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的验证码类型: " + code);
    }
}
