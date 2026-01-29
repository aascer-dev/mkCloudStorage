# RBAC 权限管理使用指南

## 概述

本项目实现了完整的 RBAC（基于角色的访问控制）权限管理系统，集成了 Sa-Token 框架，提供了用户、角色、权限的完整管理功能。

## 数据库表结构

### 核心表
- `users` - 用户表
- `roles` - 角色表
- `permissions` - 权限表
- `user_roles` - 用户角色关联表
- `role_permissions` - 角色权限关联表

### 关系说明
- 用户 ↔ 角色：多对多关系（通过 user_roles 表）
- 角色 ↔ 权限：多对多关系（通过 role_permissions 表）
- 用户通过角色获得权限

## API 接口

### 1. 查询用户权限信息

#### 查询指定用户的完整权限信息
```http
GET /api/rbac/user/{userId}/permissions
```

响应示例：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 1,
    "username": "admin",
    "nickname": "管理员",
    "roles": [
      {
        "roleId": 1,
        "roleName": "ROLE_ADMIN",
        "roleDescription": "系统管理员",
        "permissions": [
          {
            "permissionId": 1,
            "permissionName": "sys:user:create",
            "permissionDescription": "创建用户"
          }
        ]
      }
    ],
    "permissions": [
      {
        "permissionId": 1,
        "permissionName": "sys:user:create",
        "permissionDescription": "创建用户"
      }
    ]
  }
}
```

#### 查询当前用户的权限信息
```http
GET /api/rbac/current/permissions
```

### 2. 查询角色和权限列表

#### 查询用户角色列表
```http
GET /api/rbac/user/{userId}/roles
GET /api/rbac/current/roles
```

#### 查询用户权限列表
```http
GET /api/rbac/user/{userId}/permissions
GET /api/rbac/current/permissions
```

### 3. 权限检查接口

#### 检查角色
```http
GET /api/rbac/user/{userId}/has-role/{roleName}
GET /api/rbac/current/has-role/{roleName}
```

#### 检查权限
```http
GET /api/rbac/user/{userId}/has-permission/{permissionName}
GET /api/rbac/current/has-permission/{permissionName}
```

#### 批量权限检查
```http
POST /api/rbac/user/{userId}/has-all-permissions
POST /api/rbac/user/{userId}/has-any-permission
```

请求体：
```json
["sys:user:create", "sys:user:update", "sys:user:delete"]
```

## 代码使用示例

### 1. 在 Service 中使用

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRolesService userRolesService;
    
    public void createUser(CreateUserRequest request) {
        // 检查当前用户是否有创建用户的权限
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (!userRolesService.hasPermission(currentUserId, "sys:user:create")) {
            throw new BusinessException("没有创建用户的权限");
        }
        
        // 创建用户逻辑...
    }
    
    public UserRolePermissionVO getUserPermissions(Long userId) {
        return userRolesService.getUserRolePermissions(userId);
    }
}
```

### 2. 使用 Sa-Token 注解

```java
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    // 要求用户拥有 ROLE_ADMIN 角色
    @SaCheckRole("ROLE_ADMIN")
    @GetMapping("/users")
    public Result<List<User>> getUsers() {
        // 管理员才能访问的接口
        return Result.success(userService.getAllUsers());
    }
    
    // 要求用户拥有指定权限
    @SaCheckPermission("sys:user:delete")
    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        // 只有拥有删除用户权限的用户才能访问
        userService.deleteUser(id);
        return Result.success();
    }
    
    // 要求用户拥有任意一个权限
    @SaCheckPermission(value = {"sys:user:view", "sys:user:list"}, mode = SaMode.OR)
    @GetMapping("/user/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        return Result.success(userService.getUser(id));
    }
}
```

### 3. 使用 RbacUtil 工具类

```java
@Service
public class FileService {
    
    public void uploadFile(MultipartFile file) {
        // 检查用户是否有文件上传权限
        if (!RbacUtil.hasPermission("file:upload")) {
            throw new BusinessException("没有文件上传权限");
        }
        
        // 检查是否为管理员
        if (RbacUtil.isAdmin()) {
            // 管理员可以上传任意类型文件
        } else {
            // 普通用户只能上传特定类型文件
            if (!RbacUtil.hasPermission("file:upload:image")) {
                throw new BusinessException("只能上传图片文件");
            }
        }
        
        // 文件上传逻辑...
    }
    
    public void deleteFile(Long fileId) {
        // 要求用户必须有删除权限，否则抛出异常
        RbacUtil.checkPermission("file:delete");
        
        // 删除文件逻辑...
    }
}
```

### 4. 在模板中使用（如果使用 Thymeleaf）

```html
<!-- 检查角色 -->
<div th:if="${@rbacUtil.hasRole('ROLE_ADMIN')}">
    <button>管理员功能</button>
</div>

<!-- 检查权限 -->
<div th:if="${@rbacUtil.hasPermission('sys:user:create')}">
    <button>创建用户</button>
</div>

<!-- 检查多个权限 -->
<div th:if="${@rbacUtil.hasAnyPermission('file:upload', 'file:create')}">
    <button>上传文件</button>
</div>
```

## 权限命名规范

### 角色命名
- `ROLE_SUPER_ADMIN` - 超级管理员
- `ROLE_ADMIN` - 系统管理员
- `ROLE_USER` - 普通用户
- `ROLE_GUEST` - 访客

### 权限命名
采用 `模块:操作:资源` 的格式：

#### 系统管理
- `sys:user:create` - 创建用户
- `sys:user:update` - 更新用户
- `sys:user:delete` - 删除用户
- `sys:user:view` - 查看用户
- `sys:role:manage` - 角色管理
- `sys:permission:manage` - 权限管理

#### 文件管理
- `file:upload` - 文件上传
- `file:download` - 文件下载
- `file:delete` - 文件删除
- `file:share` - 文件分享
- `file:manage` - 文件管理

#### 存储管理
- `storage:bucket:create` - 创建存储桶
- `storage:bucket:delete` - 删除存储桶
- `storage:quota:manage` - 配额管理

## 最佳实践

### 1. 权限设计原则
- **最小权限原则**：用户只获得完成工作所需的最小权限
- **职责分离**：不同职责的权限分开设计
- **权限继承**：通过角色继承实现权限的层次化管理

### 2. 角色设计
- 根据业务职能设计角色，而不是根据人员设计
- 角色应该是稳定的，权限可以动态调整
- 避免角色爆炸，合理控制角色数量

### 3. 权限检查
- 在业务逻辑层进行权限检查，而不仅仅在控制器层
- 对敏感操作进行多重权限检查
- 记录权限检查失败的日志，便于审计

### 4. 性能优化
- 权限信息可以缓存到 Redis 中
- 避免在循环中进行权限检查
- 批量权限检查优于单个权限检查

## 扩展功能

### 1. 数据权限
可以扩展实现数据级别的权限控制，如：
- 用户只能查看自己的数据
- 部门管理员只能管理本部门数据
- 区域管理员只能管理本区域数据

### 2. 动态权限
可以实现运行时动态调整权限：
- 基于时间的权限（如工作时间才有某些权限）
- 基于地理位置的权限
- 基于设备的权限

### 3. 权限审计
记录所有权限相关的操作：
- 权限检查日志
- 权限变更日志
- 登录和操作日志

## 故障排查

### 1. 权限不生效
- 检查用户是否正确分配了角色
- 检查角色是否正确分配了权限
- 检查 Sa-Token 配置是否正确
- 检查 StpInterfaceImpl 是否正确实现

### 2. 性能问题
- 检查是否有 N+1 查询问题
- 考虑添加适当的数据库索引
- 使用缓存减少数据库查询

### 3. 权限检查失败
- 检查权限名称是否正确
- 检查用户是否已登录
- 检查数据库中的权限数据是否正确