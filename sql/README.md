# MKCS 数据库迁移

应用使用 Flyway 自动执行 `mkcs-server/src/main/resources/db/migration/` 中的版本化 SQL。每次启动都会先校验迁移历史，再执行尚未应用的迁移；执行记录保存于 `flyway_schema_history`。

## 新建空库

只需创建数据库并启动应用。Flyway 会依次执行 `V1__initial_schema.sql` 和后续迁移，不需要手动 `SOURCE` SQL 文件。

```sql
CREATE DATABASE mkCloudStorage CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 已有数据库

首次接入的非空数据库会自动记录 V1 基线，并执行其后的增量迁移。迁移脚本必须保持不可变；不要手工插入、修改或删除 `flyway_schema_history` 记录。

`mkCloudStorage.sql` 是当前 schema 快照，只用于人工检查和受控恢复。它包含 `DROP TABLE`，绝不能作为常规部署或升级入口。

## 新增迁移

在 `mkcs-server/src/main/resources/db/migration/` 新建 `V<version>__<description>.sql`，例如 `V20260918_001__add_share_expiry_index.sql`。一次结构变更使用一个新文件；已在任何环境执行过的迁移不得修改。破坏性改动必须先兼容发布、回填和验证，再在后续迁移中清理旧结构。
