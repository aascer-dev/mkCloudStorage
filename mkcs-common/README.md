# MKCS Common Module

## Sa-Token Redis DAO 实现

### 概述

`MkcsSaTokenDao` 是 Sa-Token 框架的 Redis 持久层实现，提供了完整的 Token 和 Session 管理功能。

### 主要特性

- **完整的 SaTokenDao 接口实现**：实现了 Sa-Token 框架要求的所有方法
- **Redis 存储支持**：使用 Redis 作为数据存储后端
- **JSON 序列化**：支持复杂对象的 JSON 序列化和反序列化
- **空值安全**：添加了完善的空值检查，提高代码健壮性
- **会话管理**：支持 Sa-Token 的会话管理功能
- **搜索功能**：支持基于前缀和关键字的数据搜索

### 功能模块

#### 1. String 数据操作
- `get(String key)` - 获取字符串值
- `set(String key, String value, long timeout)` - 设置字符串值和过期时间
- `update(String key, String value)` - 更新字符串值（保持原有过期时间）
- `delete(String key)` - 删除键值对
- `getTimeout(String key)` - 获取键的过期时间
- `updateTimeout(String key, long timeout)` - 更新键的过期时间

#### 2. Object 数据操作
- `getObject(String key)` - 获取对象
- `getObject(String key, Class<T> cs)` - 获取指定类型的对象
- `setObject(String key, Object object, long timeout)` - 设置对象和过期时间
- `updateObject(String key, Object object)` - 更新对象（保持原有过期时间）
- `deleteObject(String key)` - 删除对象
- `getObjectTimeout(String key)` - 获取对象的过期时间
- `updateObjectTimeout(String key, long timeout)` - 更新对象的过期时间

#### 3. Session 管理
- `getSession(String sessionId)` - 获取会话
- `setSession(SaSession session, long timeout)` - 设置会话和过期时间
- `updateSession(SaSession session)` - 更新会话
- `deleteSession(String sessionId)` - 删除会话
- `getSessionTimeout(String sessionId)` - 获取会话过期时间
- `updateSessionTimeout(String sessionId, long timeout)` - 更新会话过期时间

#### 4. 数据搜索
- `searchData(String prefix, String keyword, int start, int size, boolean sortType)` - 搜索数据

### 配置要求

#### 1. Redis 配置
确保在 `application.yml` 中配置了 Redis 连接信息：

```yaml
spring:
  data:
    redis:
      host: ${mkcs.redis.host}
      port: ${mkcs.redis.port}
      password: ${mkcs.redis.password}
      database: ${mkcs.redis.database}
      timeout: 10000ms
      lettuce:
        pool:
          max-active: 8
          max-wait: -1ms
          max-idle: 8
          min-idle: 0
```

#### 2. Sa-Token 配置
在 `application.yml` 中配置 Sa-Token：

```yaml
sa-token:
  token-name: mkcs
  timeout: 7200
  active-timeout: -1
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-log: true
```

### 依赖要求

确保在 `pom.xml` 中包含以下依赖：

```xml
<!-- Sa-Token -->
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>

<!-- Spring Data Redis -->
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-redis</artifactId>
</dependency>

<!-- Spring Boot Web (包含 Jackson) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 使用方式

该实现会自动被 Spring 容器管理，Sa-Token 框架会自动发现并使用这个 DAO 实现。无需额外配置，只要确保：

1. 类在 Spring 的组件扫描路径下
2. Redis 连接配置正确
3. 相关依赖已添加

### 注意事项

1. **线程安全**：该实现是线程安全的，可以在多线程环境中使用
2. **异常处理**：对于 JSON 序列化/反序列化异常，会抛出 `RuntimeException`
3. **空值处理**：所有方法都进行了空值检查，避免 `NullPointerException`
4. **性能考虑**：使用了 Redis 的原生操作，性能良好

### 扩展说明

如果需要自定义序列化方式或添加其他功能，可以：

1. 修改 `JacksonConfig` 配置类来自定义 JSON 序列化行为
2. 继承 `MkcsSaTokenDao` 类并重写相关方法
3. 实现自定义的键名生成策略