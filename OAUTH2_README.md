# GitHub OAuth2 登录功能

## 🚀 快速开始

只需 3 步即可启用 GitHub OAuth2 登录：

### 1️⃣ 创建 GitHub OAuth App
访问 https://github.com/settings/developers 创建 OAuth App

### 2️⃣ 配置应用
编辑 `mkcs-server/src/main/resources/application-dev.yml`：
```yaml
oauth2:
  github:
    client-id: 你的_Client_ID
    client-secret: 你的_Client_Secret
```

### 3️⃣ 启动测试
```bash
mvn spring-boot:run
```
访问 http://localhost:8080/oauth2-demo.html

## 📚 文档导航

### 新手入门
- [快速开始指南](GITHUB_OAUTH2_QUICKSTART.md) - 详细的 3 步配置教程
- [测试指南](OAUTH2_TEST_GUIDE.md) - 完整的测试流程

### 开发文档
- [使用文档](mkcs-server/OAUTH2_USAGE.md) - API 接口和前端集成
- [重定向流程](mkcs-server/OAUTH2_REDIRECT_FLOW.md) - 登录流程详解
- [头像更新](mkcs-server/OAUTH2_AVATAR_UPDATE.md) - 头像保存机制

### 问题排查
- [静态资源配置](mkcs-server/SATOKEN_STATIC_RESOURCES.md) - 解决拦截问题
- [Favicon 修复](mkcs-server/FAVICON_WARNING_FIX.md) - 消除警告

### 项目总结
- [实现总结](OAUTH2_IMPLEMENTATION_SUMMARY.md) - 完整的实现说明

## ✨ 核心特性

- ✅ 一键登录，无需注册
- ✅ 自动保存 GitHub 头像
- ✅ 完整的用户流程
- ✅ 支持前后端分离
- ✅ 易于扩展其他平台

## 🎯 示例页面

- `/oauth2-demo.html` - OAuth2 登录示例
- `/index.html` - 主页示例

## 🔗 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/auth/oauth2/github/authorize` | 获取授权 URL |
| POST | `/api/auth/oauth2/github/callback` | 处理回调（JSON） |
| GET | `/api/auth/oauth2/github/callback` | 处理回调（重定向） |

## 💡 使用场景

### 场景 1：快速测试
使用内置的 `oauth2-demo.html` 页面快速测试功能

### 场景 2：前端项目集成
配置 `frontend-callback-url` 为前端地址，实现前后端分离

### 场景 3：生产部署
更新配置为生产域名，启用 HTTPS

## 🆘 需要帮助？

1. 查看 [快速开始指南](GITHUB_OAUTH2_QUICKSTART.md)
2. 查看 [测试指南](OAUTH2_TEST_GUIDE.md)
3. 查看 [使用文档](mkcs-server/OAUTH2_USAGE.md)
4. 查看 [实现总结](OAUTH2_IMPLEMENTATION_SUMMARY.md)

## 📝 更新日志

### v1.0.0 (2026-02-07)
- ✅ 实现 GitHub OAuth2 登录
- ✅ 自动保存用户头像
- ✅ 支持重定向跳转
- ✅ 提供示例页面
- ✅ 完整文档

---

**开始使用吧！** 🎉
