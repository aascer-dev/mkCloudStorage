# 数据库字段重命名说明

## 概述
将所有表的时间字段从 `created_time` 和 `update_time` 重命名为 `created_at` 和 `updated_at`。

## 执行步骤

### 1. 数据库迁移
按顺序执行以下SQL脚本：

```bash
# 1. 重命名时间字段
mysql -u [username] -p [database_name] < 05_rename_time_fields.sql
```

### 2. 已修改的Java文件

#### 实体类相关
- `mkcs-model/src/main/java/cn/zjj/mkcsmodel/entity/BaseEntity.java`
  - `createdTime` → `createdAt`
  - `updateTime` → `updatedAt`
  - 更新了 `@TableField` 注解中的数据库字段名

#### 处理器类
- `mkcs-server/src/main/java/cn/zjj/mkcsserver/handler/MyMetaObjectHandler.java`
  - 更新了自动填充字段名

#### 转换器类
- `mkcs-server/src/main/java/cn/zjj/mkcsserver/converter/UserConverter.java`
  - 更新了字段映射

#### VO类
- `mkcs-model/src/main/java/cn/zjj/mkcsmodel/vo/UserInfoResponse.java`
  - 更新了响应字段名

#### 控制器类
- `mkcs-server/src/main/java/cn/zjj/mkcsserver/controller/AuthController.java`
  - 更新了测试数据构建

### 3. 验证步骤

1. 执行数据库迁移脚本
2. 重新编译项目：`mvn clean compile`
3. 运行测试：`mvn test`
4. 启动应用并验证API功能

## 注意事项

- 执行数据库迁移前请务必备份数据库
- 确保应用停止后再执行数据库迁移
- 迁移完成后重启应用
- 所有继承 `BaseEntity` 的实体类会自动使用新的字段名

## 回滚方案

如需回滚，可以执行相反的操作：

```sql
-- 回滚脚本（谨慎使用）
ALTER TABLE users CHANGE COLUMN created_at created_time DATETIME COMMENT '创建时间';
ALTER TABLE users CHANGE COLUMN updated_at update_time DATETIME COMMENT '更新时间';
-- ... 对所有表执行相同操作
```