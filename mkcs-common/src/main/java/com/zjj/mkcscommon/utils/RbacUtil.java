package com.zjj.mkcscommon.utils;

import cn.dev33.satoken.stp.StpUtil;

import java.util.List;

/**
 * RBAC权限工具类
 * 
 * @author zjj
 */
public class RbacUtil {

    /**
     * 检查当前用户是否拥有指定角色
     *
     * @param role 角色名称
     * @return 是否拥有该角色
     */
    public static boolean hasRole(String role) {
        return StpUtil.hasRole(role);
    }

    /**
     * 检查当前用户是否拥有指定权限
     *
     * @param permission 权限名称
     * @return 是否拥有该权限
     */
    public static boolean hasPermission(String permission) {
        return StpUtil.hasPermission(permission);
    }

    /**
     * 检查当前用户是否拥有所有指定角色
     *
     * @param roles 角色名称数组
     * @return 是否拥有所有角色
     */
    public static boolean hasAllRoles(String... roles) {
        for (String role : roles) {
            if (!StpUtil.hasRole(role)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查当前用户是否拥有任意一个指定角色
     *
     * @param roles 角色名称数组
     * @return 是否拥有任意一个角色
     */
    public static boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (StpUtil.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前用户是否拥有所有指定权限
     *
     * @param permissions 权限名称数组
     * @return 是否拥有所有权限
     */
    public static boolean hasAllPermissions(String... permissions) {
        for (String permission : permissions) {
            if (!StpUtil.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查当前用户是否拥有任意一个指定权限
     *
     * @param permissions 权限名称数组
     * @return 是否拥有任意一个权限
     */
    public static boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (StpUtil.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查当前用户是否拥有所有指定权限（列表形式）
     *
     * @param permissions 权限名称列表
     * @return 是否拥有所有权限
     */
    public static boolean hasAllPermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return true;
        }
        return hasAllPermissions(permissions.toArray(new String[0]));
    }

    /**
     * 检查当前用户是否拥有任意一个指定权限（列表形式）
     *
     * @param permissions 权限名称列表
     * @return 是否拥有任意一个权限
     */
    public static boolean hasAnyPermission(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return hasAnyPermission(permissions.toArray(new String[0]));
    }

    /**
     * 获取当前用户的所有角色
     *
     * @return 角色列表
     */
    public static List<String> getRoles() {
        return StpUtil.getRoleList();
    }

    /**
     * 获取当前用户的所有权限
     *
     * @return 权限列表
     */
    public static List<String> getPermissions() {
        return StpUtil.getPermissionList();
    }

    /**
     * 检查当前用户是否为管理员
     * 默认检查是否拥有 ROLE_ADMIN 角色
     *
     * @return 是否为管理员
     */
    public static boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }

    /**
     * 检查当前用户是否为超级管理员
     * 默认检查是否拥有 ROLE_SUPER_ADMIN 角色
     *
     * @return 是否为超级管理员
     */
    public static boolean isSuperAdmin() {
        return hasRole("ROLE_SUPER_ADMIN");
    }

    /**
     * 要求当前用户拥有指定角色，否则抛出异常
     *
     * @param role 角色名称
     */
    public static void checkRole(String role) {
        StpUtil.checkRole(role);
    }

    /**
     * 要求当前用户拥有指定权限，否则抛出异常
     *
     * @param permission 权限名称
     */
    public static void checkPermission(String permission) {
        StpUtil.checkPermission(permission);
    }

    /**
     * 要求当前用户拥有所有指定角色，否则抛出异常
     *
     * @param roles 角色名称数组
     */
    public static void checkAllRoles(String... roles) {
        for (String role : roles) {
            StpUtil.checkRole(role);
        }
    }

    /**
     * 要求当前用户拥有所有指定权限，否则抛出异常
     *
     * @param permissions 权限名称数组
     */
    public static void checkAllPermissions(String... permissions) {
        for (String permission : permissions) {
            StpUtil.checkPermission(permission);
        }
    }
}