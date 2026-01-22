# 通用返回结果和异常处理使用说明

## 概述

本项目提供了一套完整的通用返回结果JSON格式和异常捕获机制，包括：

- `Result<T>` - 通用返回结果类
- `ResultCode` - 返回结果码枚举
- `BusinessException` - 业务异常类
- `GlobalExceptionHandler` - 全局异常处理器
- `Assert` - 断言工具类
- **Sa-Token统一返回格式** - 认证授权异常统一处理

## Sa-Token统一返回格式配置

本项目已经配置了Sa-Token与自定义Result格式的统一返回，所有Sa-Token相关的异常都会返回统一的JSON格式，不会出现默认的SaResult格式。

### Sa-Token异常统一处理

项目已经配置了以下Sa-Token异常的统一处理：

1. **NotLoginException** - 未登录异常
   - NOT_TOKEN: "未提供Token"
   - INVALID_TOKEN: "Token无效"
   - TOKEN_TIMEOUT: "Token已过期"
   - BE_REPLACED: "Token已被顶下线"
   - KICK_OUT: "Token已被踢下线"

2. **NotPermissionException** - 权限不足异常
3. **NotRoleException** - 角色不足异常
4. **SaTokenException** - 其他Sa-Token异常

### 认证相关接口测试

#### 登录接口
```
POST /api/auth/login
Content-Type: application/json

{
    "username": "admin",
    "password": "123456",
    "rememberMe": true,
    "captcha": "1234",
    "captchaKey": "captcha_key_123"
}
```

成功响应：
```json
{
    "code": 200,
    "message": "登录成功",
    "data": {
        "token": "your-token-here",
        "tokenType": "Bearer",
        "expiresIn": 604800,
        "id": 1001,
        "currentBucketId": 1,
        "username": "admin",
        "nickname": "管理员",
        "email": "admin@example.com",
        "avatarUrl": "https://example.com/avatar.jpg",
        "status": 1,
        "roles": ["ROLE_ADMIN", "ROLE_USER"],
        "permissions": ["file:read", "file:write", "file:delete", "sys:user:ban"],
        "rememberMe": true
    },
    "timestamp": 1642752000000
}
```

**Remember Me功能说明：**
- `rememberMe: true` - 7天有效期
- `rememberMe: false` - 2小时有效期

#### 登出接口
```
POST /api/auth/logout
Authorization: Bearer your-token-here
```

#### 获取用户信息
```
GET /api/auth/userinfo
Authorization: Bearer your-token-here
```

成功响应：
```json
{
    "code": 200,
    "message": "获取用户信息成功",
    "data": {
        "id": 1001,
        "currentBucketId": 1,
        "username": "admin",
        "nickname": "管理员",
        "email": "admin@example.com",
        "avatarUrl": "https://example.com/avatar.jpg",
        "status": 1,
        "roles": ["ROLE_ADMIN"],
        "permissions": ["file:read", "file:write", "file:delete"],
        "createdTime": "2023-12-01T10:00:00",
        "updateTime": "2024-01-01T10:00:00"
    },
    "timestamp": 1642752000000
}
```

#### 刷新Token
```
POST /api/auth/refresh
Authorization: Bearer your-token-here
```

#### 检查登录状态
```
GET /api/auth/check
```

#### 受保护接口测试

```
GET /api/example/need-login
Authorization: Bearer your-token-here
```

未登录时返回：
```json
{
    "code": 401,
    "message": "当前会话未登录",
    "data": null,
    "timestamp": 1642752000000
}
```

权限不足时返回：
```json
{
    "code": 2004,
    "message": "权限不足，需要权限: user:delete",
    "data": null,
    "timestamp": 1642752000000
}
```

## 使用方法

### 1. 控制器返回结果

```java
@RestController
public class UserController {
    
    // 成功返回数据
    @GetMapping("/user/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        return Result.success(user);
    }
    
    // 成功返回（无数据）
    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
        return Result.success();
    }
    
    // 自定义成功消息
    @PostMapping("/user")
    public Result<User> createUser(@RequestBody User user) {
        User savedUser = userService.save(user);
        return Result.success("用户创建成功", savedUser);
    }
}
```

### 2. 抛出业务异常

```java
@Service
public class UserService {
    
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            // 方式1：直接抛出异常
            throw new BusinessException("用户不存在");
            
            // 方式2：使用结果码枚举
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
            
            // 方式3：使用Assert工具类
            Assert.notNull(user, "用户不存在");
        }
        return user;
    }
}
```

### 3. 使用Assert工具类

```java
@Service
public class UserService {
    
    public void updateUser(User user) {
        // 断言参数不为空
        Assert.notNull(user, "用户信息不能为空");
        Assert.notEmpty(user.getName(), "用户名不能为空");
        
        // 断言业务逻辑
        Assert.isTrue(user.getAge() >= 18, "用户年龄必须大于等于18岁");
        
        // 断言数据存在
        User existUser = userMapper.selectById(user.getId());
        Assert.notNull(existUser, ResultCode.DATA_NOT_FOUND);
        
        userMapper.updateById(user);
    }
}
```

### 4. 参数校验

```java
@RestController
public class UserController {
    
    @PostMapping("/user")
    public Result<User> createUser(@Valid @RequestBody UserRequest request) {
        // 参数校验失败会自动被GlobalExceptionHandler捕获
        User user = userService.create(request);
        return Result.success(user);
    }
}

public class UserRequest {
    @NotBlank(message = "用户名不能为空")
    private String name;
    
    @NotNull(message = "年龄不能为空")
    @Min(value = 18, message = "年龄必须大于等于18岁")
    private Integer age;
    
    // getter/setter...
}
```

## 返回结果格式

### 成功响应
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "id": 1,
        "name": "张三",
        "age": 25
    },
    "timestamp": 1642752000000
}
```

### 失败响应
```json
{
    "code": 1001,
    "message": "数据不存在",
    "data": null,
    "timestamp": 1642752000000
}
```

## 结果码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 操作成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 422 | 参数校验失败 |
| 500 | 系统内部错误 |
| 1000 | 业务处理失败 |
| 1001 | 数据不存在 |
| 1002 | 数据已存在 |
| 2001 | Token无效 |
| 2002 | Token已过期 |
| 3001 | 缺少必要参数 |
| 3002 | 参数格式错误 |

## 异常处理机制

全局异常处理器会自动捕获以下异常：

1. **BusinessException** - 业务异常，返回自定义错误码和消息
2. **MethodArgumentNotValidException** - @RequestBody参数校验异常
3. **BindException** - @ModelAttribute参数校验异常
4. **ConstraintViolationException** - @RequestParam参数校验异常
5. **MethodArgumentTypeMismatchException** - 参数类型不匹配异常
6. **IllegalArgumentException** - 非法参数异常
7. **NullPointerException** - 空指针异常
8. **RuntimeException** - 运行时异常
9. **Exception** - 其他异常

## 测试接口

项目提供了示例控制器 `ExampleController`，包含以下测试接口：

- `GET /api/example/success` - 成功返回示例
- `GET /api/example/business-error` - 业务异常示例
- `GET /api/example/assert-error?name=xx` - Assert工具类示例
- `POST /api/example/validation-error` - 参数校验异常示例
- `GET /api/example/system-error` - 系统异常示例

可以使用这些接口测试异常处理机制是否正常工作。