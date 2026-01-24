package com.zjj.mkcscommon;

import java.util.Collection;
import java.util.Objects;

/**
 * 断言工具类，用于抛出业务异常
 */
public class Assert {
    
    /**
     * 断言对象不为空，为空则抛出异常
     * @param object 对象
     * @param message 异常消息
     */
    public static void notNull(Object object, String message) {
        if (object == null) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言对象不为空，为空则抛出异常
     * @param object 对象
     * @param resultCode 结果码
     */
    public static void notNull(Object object, ResultCode resultCode) {
        if (object == null) {
            throw new BusinessException(resultCode);
        }
    }
    
    /**
     * 断言字符串不为空，为空则抛出异常
     * @param str 字符串
     * @param message 异常消息
     */
    public static void notEmpty(String str, String message) {
        if (str == null || str.trim().isEmpty()) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言字符串不为空，为空则抛出异常
     * @param str 字符串
     * @param resultCode 结果码
     */
    public static void notEmpty(String str, ResultCode resultCode) {
        if (str == null || str.trim().isEmpty()) {
            throw new BusinessException(resultCode);
        }
    }
    
    /**
     * 断言集合不为空，为空则抛出异常
     * @param collection 集合
     * @param message 异常消息
     */
    public static void notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言集合不为空，为空则抛出异常
     * @param collection 集合
     * @param resultCode 结果码
     */
    public static void notEmpty(Collection<?> collection, ResultCode resultCode) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(resultCode);
        }
    }
    
    /**
     * 断言表达式为真，为假则抛出异常
     * @param expression 表达式
     * @param message 异常消息
     */
    public static void isTrue(boolean expression, String message) {
        if (!expression) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言表达式为真，为假则抛出异常
     * @param expression 表达式
     * @param resultCode 结果码
     */
    public static void isTrue(boolean expression, ResultCode resultCode) {
        if (!expression) {
            throw new BusinessException(resultCode);
        }
    }
    
    /**
     * 断言表达式为假，为真则抛出异常
     * @param expression 表达式
     * @param message 异常消息
     */
    public static void isFalse(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言表达式为假，为真则抛出异常
     * @param expression 表达式
     * @param resultCode 结果码
     */
    public static void isFalse(boolean expression, ResultCode resultCode) {
        if (expression) {
            throw new BusinessException(resultCode);
        }
    }
    
    /**
     * 断言两个对象相等，不相等则抛出异常
     * @param obj1 对象1
     * @param obj2 对象2
     * @param message 异常消息
     */
    public static void equals(Object obj1, Object obj2, String message) {
        if (!Objects.equals(obj1, obj2)) {
            throw new BusinessException(message);
        }
    }
    
    /**
     * 断言两个对象相等，不相等则抛出异常
     * @param obj1 对象1
     * @param obj2 对象2
     * @param resultCode 结果码
     */
    public static void equals(Object obj1, Object obj2, ResultCode resultCode) {
        if (!Objects.equals(obj1, obj2)) {
            throw new BusinessException(resultCode);
        }
    }
    
    /**
     * 直接抛出业务异常
     * @param message 异常消息
     */
    public static void fail(String message) {
        throw new BusinessException(message);
    }
    
    /**
     * 直接抛出业务异常
     * @param resultCode 结果码
     */
    public static void fail(ResultCode resultCode) {
        throw new BusinessException(resultCode);
    }
}