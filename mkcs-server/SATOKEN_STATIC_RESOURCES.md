# Sa-Token 静态资源配置说明

## 问题描述

访问静态资源（如 `/oauth2-demo.html`）时被 Sa-Token 拦截器拦截，返回 401 未授权错误。

## 原因分析

Sa-Token 的全局过滤器配置在 `SaTokenExceptionHandler.java` 中，默认拦截所有路径 `/**`，但没有排除静态资源路径。

## 解决方案

在 `SaTokenExceptionHandler.java` 中添加静态资源的排除规则：

### 1. 在 `addExclude` 中添加

```java
.addExclude("/static/**")        // 排除 static 目录
.addExclude("/*.html")           // 排除根目录下的 HTML 文件
.addExclude("/*.css")            // 排除根目录下的 CSS 文件
.addExclude("/*.js")             // 排除根目录下的 JS 文件
```

### 2. 在 `setAuth` 的 `notMatch` 中添加

```java
.notMatch("/static/**")      // 排除 static 目录
.notMatch("/*.html")         // 排除根目录下的 HTML 文件
.notMatch("/*.css")          // 排除根目录下的 CSS 文件
.notMatch("/*.js")           // 排除根目录下的 JS 文件
```

## 完整配置

```java
@Bean
public SaServletFilter getSaServletFilter() {
    return new SaServletFilter()
        // 指定[拦截路由]与[放行路由]
        .addInclude("/**")
        .addExclude("/favicon.ico")
        .addExclude("/swagger-ui/**")
        .addExclude("/swagger-ui.html")
        .addExclude("/v3/api-docs/**")
        .addExclude("/v3/api-docs")
        .addExclude("/swagger-resources/**")
        .addExclude("/webjars/**")
        .addExclude("/static/**")        // ✅ 静态资源
        .addExclude("/*.html")           // ✅ HTML 文件
        .addExclude("/*.css")            // ✅ CSS 文件
        .addExclude("/*.js")             // ✅ JS 文件
        
        // 认证函数
        .setAuth(obj -> {
            SaRouter.match("/**")
                .notMatch("/api/auth/**")
                .notMatch("/error")
                .notMatch("/favicon.ico")
                .notMatch("/swagger-ui/**")
                .notMatch("/swagger-ui.html")
                .notMatch("/v3/api-docs/**")
                .notMatch("/v3/api-docs")
                .notMatch("/swagger-resources/**")
                .notMatch("/webjars/**")
                .notMatch("/static/**")      // ✅ 静态资源
                .notMatch("/*.html")         // ✅ HTML 文件
                .notMatch("/*.css")          // ✅ CSS 文件
                .notMatch("/*.js")           // ✅ JS 文件
                .check(r -> StpUtil.checkLogin());
        })
        // ... 其他配置
}
```

## 静态资源访问路径

### Spring Boot 默认静态资源路径

Spring Boot 会自动映射以下目录为静态资源：

- `classpath:/static/`
- `classpath:/public/`
- `classpath:/resources/`
- `classpath:/META-INF/resources/`

### 访问方式

1. **直接访问根路径下的文件**
   - 文件位置：`src/main/resources/static/oauth2-demo.html`
   - 访问 URL：`http://localhost:8080/oauth2-demo.html`

2. **访问 static 子目录下的文件**
   - 文件位置：`src/main/resources/static/css/style.css`
   - 访问 URL：`http://localhost:8080/css/style.css`

3. **使用 /static 前缀访问**
   - 文件位置：`src/main/resources/static/oauth2-demo.html`
   - 访问 URL：`http://localhost:8080/static/oauth2-demo.html`
   - 需要在 `WebMvcConfiguration` 中配置：
     ```java
     registry.addResourceHandler("/static/**")
             .addResourceLocations("classpath:/static/");
     ```

## 排除规则说明

### addExclude vs notMatch

- **addExclude**：在过滤器级别排除，不会进入 Sa-Token 过滤器
- **notMatch**：在路由匹配级别排除，会进入过滤器但不执行认证检查

建议：两者都配置，确保静态资源完全不受拦截。

### 通配符说明

- `/**`：匹配所有路径和子路径
- `/*.html`：只匹配根路径下的 HTML 文件
- `/static/**`：匹配 /static 目录下的所有文件

## 常见问题

### Q1: 为什么要同时配置 addExclude 和 notMatch？

**A:** 双重保险，确保静态资源在任何情况下都不会被拦截。

### Q2: 如果只想排除特定的 HTML 文件怎么办？

**A:** 使用精确匹配：
```java
.addExclude("/oauth2-demo.html")
.notMatch("/oauth2-demo.html")
```

### Q3: 如何排除整个目录？

**A:** 使用 `/**` 通配符：
```java
.addExclude("/public/**")
.notMatch("/public/**")
```

### Q4: 静态资源需要认证怎么办？

**A:** 不要在排除列表中添加该资源，Sa-Token 会自动拦截并要求登录。

## 测试

### 1. 测试静态资源访问

```bash
# 应该返回 HTML 内容，而不是 401 错误
curl http://localhost:8080/oauth2-demo.html
```

### 2. 测试 API 接口拦截

```bash
# 应该返回 401 未授权错误
curl http://localhost:8080/api/users

# 应该正常访问（认证接口不需要登录）
curl http://localhost:8080/api/auth/login
```

## 相关文件

- `SaTokenExceptionHandler.java` - Sa-Token 过滤器配置
- `WebMvcConfiguration.java` - Spring MVC 配置
- `oauth2-demo.html` - OAuth2 示例页面

## 总结

通过在 Sa-Token 过滤器中添加静态资源的排除规则，解决了静态资源被拦截的问题。现在可以正常访问：

- ✅ `/oauth2-demo.html` - OAuth2 登录示例页面
- ✅ `/swagger-ui.html` - Swagger API 文档
- ✅ 其他静态资源文件

同时保持了 API 接口的认证保护。
