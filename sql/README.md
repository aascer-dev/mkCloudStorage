# MKCS 数据库脚本

本目录是唯一的数据库脚本来源。应用没有集成 Flyway 或 Liquibase，也不会在启动时自动执行 SQL；执行权限和执行记录由部署人员负责。

## 新建空库

`mkCloudStorage.sql` 是可重建的基线脚本，含 `DROP TABLE`，只能用于新建、明确允许重建的数据库。创建数据库后执行它即可获得当前完整结构、随机位置校验字段和 `ROLE_USER` 最小 RBAC 种子。

```sql
CREATE DATABASE mkCloudStorage CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mkCloudStorage;
SOURCE sql/mkCloudStorage.sql;
```

不要在已承载数据的环境执行基线脚本，也不要对新库重复执行已被基线吸收的历史补丁。

## 已有数据库

先备份，再按文件名升序执行尚未执行的 `VYYYYMMDD_NNN__*.sql`。当前补丁顺序如下：

1. `V20260916_001__add_file_content_random_checksum.sql`：补齐秒传随机位置校验字段；可重复执行。
2. `V20260916_002__seed_default_user_role.sql`：补齐注册需要的 `ROLE_USER`；可重复执行。

每次执行后记录环境、脚本名、执行时间、操作者和 `SHOW CREATE TABLE`/关键查询结果。结构升级后至少确认：`file_contents.content_hash` 唯一、`reference_count` 与三个随机校验字段存在，且 `roles` 中存在 `ROLE_USER`。

## 回滚与边界

补丁 `001` 的新增列均为可空列；只有回退所有读取这些列的应用实例后才可移除。补丁 `002` 不应删除仍被 `user_roles` 引用的角色。任何破坏性迁移必须新增版本化脚本，先进行兼容发布和数据回填，不能修改已在环境执行过的版本文件。
