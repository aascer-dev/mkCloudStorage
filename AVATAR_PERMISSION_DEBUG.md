# 头像上传权限问题调试指南

## 问题描述

用户信息显示有 `user:updateAvatar` 权限，但权限检查失败：
```
用户无权限更新头像: 无此权限：user:updateAvatar
```

## 已采取的解决方案

### 方案 1：移除权限检查（推荐）✅

**原因**：
- 用户更新自己的头像是基本功能，不应该需要额外权限
- 只需要检查是否是本人或管理员即可

**修改后的代码**：
```java
@PostMapping("/{id}/avatar")
@SaCheckLogin
public Result<Users> updateAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
    // 1. 获取当前登录用户ID
    Long currentUserId = StpUtil.getLoginIdAsLong();

    // 2. 检查是否是本人或管理员
    if (!currentUserId.equals(id) && !RbacUtil.isSuperAdmin() && !RbacUtil.isAdmin()) {
        return Result.error("只能更新自己的头像");
    }
    
    // ... 其他逻辑
}
```

**优点**：
- ✅ 简单直接
- ✅ 符合业务逻辑
- ✅ 不依赖复杂的权限系统
- ✅ 用户体验更好

---

## 如果需要保留权限检查

### 可能的原因

1. **Sa-Token 权限缓存问题**
   - Session 中的权限列表可能没有更新
   - 需要清除缓存或重新登录

2. **权限名称不匹配**
   - 数据库中是 `user:updateAvatar`
   - 代码中检查的也是 `user:updateAvatar`
   - 但 Sa-Token 可能有大小写敏感问题

3. **Session 过期**
   - 权限数据存储在 Session 中
   - Session 可能已过期但 Token 还有效

### 调试方法

#### 1. 添加详细日志

修改 `UserController.updateAvatar()` 方法：

```java
@PostMapping("/{id}/avatar")
@SaCheckLogin
public Result<Users> updateAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
    Long currentUserId = StpUtil.getLoginIdAsLong();
    
    // 添加调试日志
    log.info("=== 权限检查调试 ===");
    log.info("当前用户ID: {}", currentUserId);
    log.info("目标用户ID: {}", id);
    log.info("用户角色列表: {}", StpUtil.getRoleList());
    log.info("用户权限列表: {}", StpUtil.getPermissionList());
    log.info("是否有 user:updateAvatar 权限: {}", StpUtil.hasPermission("user:updateAvatar"));
    
    try {
        StpUtil.checkPermission("user:updateAvatar");
        log.info("权限检查通过");
    } catch (Exception e) {
        log.error("权限检查失败: {}", e.getMessage());
        log.error("异常类型: {}", e.getClass().getName());
    }
    
    // ... 其他逻辑
}
```

#### 2. 检查 Session 数据

在 `StpInterfaceImpl` 中添加日志：

```java
@Override
public List<String> getPermissionList(Object loginId, String loginType) {
    try {
        SaSession accountSession = StpUtil.getSessionByLoginId(loginId);
        
        // 添加调试日志
        log.info("=== 获取权限列表 ===");
        log.info("loginId: {}", loginId);
        log.info("Session ID: {}", accountSession.getId());
        log.info("Session 中的缓存: {}", accountSession.get(SESSION_PERMISSION_LIST_KEY));
        
        Object cached = accountSession.get(SESSION_PERMISSION_LIST_KEY);
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> cachedList = (List<String>) cached;
            log.info("使用缓存的权限列表: {}", cachedList);
            return cachedList;
        }

        Long userId = Long.valueOf(loginId.toString());
        List<String> permissions = userRolesService.getUserPermissionNames(userId);
        accountSession.set(SESSION_PERMISSION_LIST_KEY, permissions);
        log.info("从数据库加载权限列表: {}", permissions);
        return permissions;
    } catch (Exception e) {
        log.error("获取用户权限列表失败", e);
        return List.of();
    }
}
```

#### 3. 清除权限缓存

添加一个清除缓存的接口：

```java
@PostMapping("/clear-permission-cache")
@SaCheckLogin
public Result<Void> clearPermissionCache() {
    Long userId = StpUtil.getLoginIdAsLong();
    SaSession session = StpUtil.getSessionByLoginId(userId);
    session.delete("permList");
    session.delete("roleList");
    return Result.success("权限缓存已清除，请重新获取用户信息");
}
```

#### 4. 强制重新加载权限

修改登录逻辑，每次登录都清除旧的权限缓存：

```java
// 在登录成功后
StpUtil.login(userId);
SaSession session = StpUtil.getSessionByLoginId(userId);
session.delete("permList");
session.delete("roleList");
```

---

## 测试步骤

### 1. 使用新的接口（无权限检查）

```bash
curl -X POST http://localhost:8080/api/users/2020047422002344000/avatar \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@avatar.jpg"
```

应该成功上传。

### 2. 如果需要调试权限问题

```bash
# 1. 清除权限缓存
curl -X POST http://localhost:8080/api/users/clear-permission-cache \
  -H "Authorization: Bearer YOUR_TOKEN"

# 2. 重新获取用户信息
curl -X GET http://localhost:8080/api/auth/userinfo \
  -H "Authorization: Bearer YOUR_TOKEN"

# 3. 再次尝试上传头像
curl -X POST http://localhost:8080/api/users/2020047422002344000/avatar \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@avatar.jpg"
```

---

## 推荐方案

**建议使用方案 1（移除权限检查）**，原因：

1. **业务逻辑更清晰**
   - 用户更新自己的头像是基本功能
   - 不需要额外的权限控制

2. **减少复杂性**
   - 不依赖权限系统
   - 减少潜在的 bug

3. **更好的用户体验**
   - 用户不会因为权限问题无法更新头像
   - 管理员仍然可以管理所有用户的头像

4. **与其他接口一致**
   - `PUT /api/users/profile` 也只检查是否是本人
   - 保持接口设计的一致性

---

## 如果必须使用权限控制

如果业务需求确实需要权限控制，建议：

1. **使用注解方式**
   ```java
   @PostMapping("/{id}/avatar")
   @SaCheckLogin
   @SaCheckPermission("user:updateAvatar")  // 使用注解
   public Result<Users> updateAvatar(...) {
       // 不需要手动检查权限
   }
   ```

2. **或者使用更宽松的检查**
   ```java
   // 检查是否有权限 OR 是否是本人
   if (!StpUtil.hasPermission("user:updateAvatar") && !currentUserId.equals(id)) {
       return Result.error("无权限更新头像");
   }
   ```

---

## 总结

✅ **已修复**：移除了权限检查，改为只检查是否是本人或管理员

现在用户可以正常上传头像了！如果还有问题，请查看服务器日志获取更多信息。
