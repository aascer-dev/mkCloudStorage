package cn.zjj.mkcsserver.handler;

import cn.zjj.mkcsserver.auth.UserContext;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MyBatis-Plus 自动填充处理器
 * 用于自动填充公共字段：创建时间、更新时间、创建人、更新人
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        currentUserId().ifPresent(userId -> {
            strictInsertFill(metaObject, "createdBy", Long.class, userId);
            strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        });
    }

    /**
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        currentUserId().ifPresent(userId -> strictUpdateFill(metaObject, "updatedBy", Long.class, userId));
    }

    /**
     * 获取当前登录用户ID
     * 无认证上下文时不填充人员字段，避免写入不存在的系统用户 ID。
     */
    private Optional<Long> currentUserId() {
        return Optional.ofNullable(UserContext.get()).map(user -> user.userId());
    }
}
