# UsersMapper 查询方法使用示例

## 概述
UsersMapper 提供了兼具性能和直观的 MyBatis-Plus 查询方法，涵盖了常见的用户查询场景。

## 查询方法分类

### 1. 基础查询方法

#### 根据用户名查询
```java
@Service
public class UserService {
    @Autowired
    private UsersMapper usersMapper;
    
    public Users getUserByUsername(String username) {
        return usersMapper.selectByUsername(username);
    }
}
```

#### 登录验证查询
```java
public Users loginUser(String loginName) {
    // 支持用户名或邮箱登录
    return usersMapper.selectByUsernameOrEmail(loginName);
}
```

#### 查询活跃用户
```java
public List<Users> getActiveUsers() {
    return usersMapper.selectActiveUsers();
}
```

### 2. 分页查询方法

#### 多条件分页查询
```java
public IPage<Users> getUserPage(int pageNum, int pageSize, 
                               String username, String nickname, 
                               String email, Byte status,
                               LocalDateTime startTime, LocalDateTime endTime) {
    Page<Users> page = new Page<>(pageNum, pageSize);
    return usersMapper.selectUserPage(page, username, nickname, email, status, startTime, endTime);
}
```

#### 关键词搜索分页
```java
public IPage<Users> searchUsers(int pageNum, int pageSize, String keyword) {
    Page<Users> page = new Page<>(pageNum, pageSize);
    return usersMapper.selectUserPageByKeyword(page, keyword);
}
```

### 3. 批量操作方法

#### 批量查询用户
```java
public List<Users> getUsersByIds(List<Long> userIds) {
    return usersMapper.selectByIds(userIds);
}
```

#### 批量更新状态
```java
public boolean batchUpdateStatus(List<Long> userIds, Byte status) {
    int updated = usersMapper.updateStatusByIds(userIds, status);
    return updated > 0;
}
```

### 4. 统计查询方法

#### 用户状态统计
```java
public List<UsersMapper.UserStatusCount> getUserStatusStats() {
    return usersMapper.selectUserStatusCount();
}
```

#### 时间段用户统计
```java
public Long getUserCountByDateRange(LocalDateTime startTime, LocalDateTime endTime) {
    return usersMapper.countUsersByDateRange(startTime, endTime);
}
```

### 5. 复杂查询方法

#### 验证用户名唯一性
```java
public boolean isUsernameAvailable(String username, Long excludeUserId) {
    return !usersMapper.existsUsernameExcludeId(username, excludeUserId);
}
```

#### 查询指定存储桶用户
```java
public List<Users> getUsersByBucket(Long bucketId) {
    return usersMapper.selectUsersByBucketId(bucketId);
}
```

## 性能优化建议

### 1. 数据库索引
```sql
-- 用户名唯一索引（必须）
CREATE UNIQUE INDEX idx_users_username ON users(username);

-- 邮箱索引
CREATE INDEX idx_users_email ON users(email);

-- 状态索引
CREATE INDEX idx_users_status ON users(status);

-- 创建时间索引
CREATE INDEX idx_users_created_at ON users(created_at);

-- 存储桶ID索引
CREATE INDEX idx_users_bucket_id ON users(current_bucket_id);

-- 复合索引（状态+创建时间）
CREATE INDEX idx_users_status_created ON users(status, created_at);
```

### 2. 查询优化技巧

#### 使用 LIMIT 1 优化单条查询
```java
// 已在 selectByUsername 等方法中使用 .last("LIMIT 1")
```

#### 避免全表扫描
```java
// 好的做法：带条件查询
List<Users> users = usersMapper.selectActiveUsers();

// 避免：无条件查询大表
// List<Users> allUsers = usersMapper.selectList(null);
```

#### 分页查询优化
```java
// 使用合理的分页大小
Page<Users> page = new Page<>(1, 20); // 每页20条

// 避免过大的分页
// Page<Users> page = new Page<>(1, 1000); // 避免
```

## 使用最佳实践

### 1. 空值检查
```java
public Users getUserByUsername(String username) {
    if (username == null || username.trim().isEmpty()) {
        return null;
    }
    return usersMapper.selectByUsername(username);
}
```

### 2. 异常处理
```java
public Users getUserByUsernameWithException(String username) {
    Users user = usersMapper.selectByUsername(username);
    if (user == null) {
        throw new BusinessException(ResultCode.USER_NOT_FOUND);
    }
    return user;
}
```

### 3. 缓存集成
```java
@Cacheable(value = "users", key = "#username")
public Users getUserByUsername(String username) {
    return usersMapper.selectByUsername(username);
}
```

### 4. 事务管理
```java
@Transactional
public boolean batchUpdateUserStatus(List<Long> userIds, Byte status) {
    return usersMapper.updateStatusByIds(userIds, status) > 0;
}
```

## 查询方法特点

### 1. 性能优化
- 使用 Lambda 表达式避免硬编码字段名
- 合理使用 LIMIT 限制结果集
- 支持索引友好的查询条件
- 避免 N+1 查询问题

### 2. 代码直观
- 方法名清晰表达查询意图
- 参数类型安全
- 支持链式调用
- 良好的注释说明

### 3. 灵活性
- 支持动态条件查询
- 可组合的查询条件
- 支持分页和排序
- 易于扩展和维护

### 4. 类型安全
- 使用 LambdaQueryWrapper 避免字符串硬编码
- 编译时检查字段名
- IDE 智能提示支持
- 重构友好