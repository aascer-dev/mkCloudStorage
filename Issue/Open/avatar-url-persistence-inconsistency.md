# 上传头像后 `avatar_url` 未持久化且旧对象被删除

## 状态

Open

## 问题描述

用户上传新头像后，当前页面可显示新头像；但 MySQL `users.avatar_url` 未更新，仍保留旧头像 URL。该旧 URL 对应的 MinIO 对象已经不存在，直接访问会返回 `NoSuchKey`。

已删除 Redis 中的用户信息后，登出并重新登录，界面仍曾显示新上传的头像。这个现象与“新登录仅从 MySQL 装载用户信息”的预期不一致，需要通过接口响应和 Sa-Token Session 内容进一步确认新头像的实际来源。

为避免将本地对象存储地址和对象键写入仓库，本文不记录完整 URL；现场证据应保存至受控日志或 Issue 附件。

## 已观察到的证据

- `users.avatar_url` 指向一个旧的 `avatar` 桶对象。
- 访问该 URL 时 MinIO 返回 `NoSuchKey`，表明对象已被删除而不是访问权限问题。
- 新头像上传请求完成后，MySQL 中的 `avatar_url` 仍为上述旧 URL。
- 前端会给每次写入用户状态的头像 URL 追加 `avatarVersion=<Date.now()>` 查询参数；该参数只用于规避浏览器缓存，不能证明 MySQL 已持久化新 URL。

## 涉及链路

`POST /api/users/{id}/avatar` 的当前时序为：

1. 从 MySQL 读取 `Users`。
2. 上传新对象到 MinIO `avatar` 桶，生成新的随机对象名。
3. 根据刚读取到的旧 `avatarUrl` 删除旧对象。
4. 调用 `user.setAvatarUrl(newAvatarUrl)`。
5. 调用 `usersService.updateById(user)` 更新 MySQL。

这不是一个分布式事务。若步骤 5 失败、被回滚，或被其他写入覆盖，步骤 3 已经删除旧对象，结果就是 MySQL 指向不存在的对象；刚上传的新对象也可能成为孤儿对象。

## 待确认的根因

- 头像上传接口对本次请求的 `usersService.updateById(user)` 是否返回 `false`，以及对应 SQL、影响行数、事务回滚日志。
- 是否存在并发的用户资料更新、OAuth 登录资料同步或其他写入，将新 `avatar_url` 覆盖为旧值。
- 排查 MySQL 时连接的库、schema、实例是否与应用运行时数据源一致。
- 删除的 Redis 键是否为当前 token 对应的 Sa-Token Session，而非仅删除普通用户缓存；重新登录后的 `/api/users/info` 原始响应、Session 中的用户对象和 MySQL 行需要在同一时点比对。
- 界面显示的新头像是否来自浏览器预览 `blob:` URL、localStorage、旧会话，还是接口返回的持久化 URL。

## 复现与取证步骤

1. 记录上传前 MySQL 中该用户的 `avatar_url`，并确认该对象存在。
2. 上传一个内容明显不同的新头像，记录接口响应、服务端日志中的用户 ID/新对象名和 `updateById` 返回值。
3. 查询 MySQL 中同一用户的 `avatar_url`，并分别确认旧对象和新对象是否存在。
4. 使用同一 token 请求 `/api/users/info`，记录原始 JSON 中的 `avatarUrl`。
5. 清除该 token 对应的 Sa-Token Session，完成登出和重新登录后，重复步骤 3、4，并核对新 Session 的用户对象。

## 期望行为

- 只有 MySQL 成功持久化新 `avatar_url` 后，才删除旧头像对象。
- MySQL 更新失败时，旧头像仍可访问，新上传对象必须清理或进入可重试的补偿流程。
- 成功上传后，当前页面、刷新页面和重新登录后的 `/api/users/info` 都返回同一个、确实存在的新头像 URL。

## 验收条件

- 覆盖 MySQL 更新失败的自动化测试：旧对象不被删除，新对象得到补偿清理，接口返回失败。
- 覆盖成功路径的自动化或集成测试：`users.avatar_url` 更新为新对象 URL，旧对象删除，新对象存在。
- 验证清除当前 Sa-Token Session、登出并重新登录后，接口返回值与 MySQL 中的 `avatar_url` 一致。
