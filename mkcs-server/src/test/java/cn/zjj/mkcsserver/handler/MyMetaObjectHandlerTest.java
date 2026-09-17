package cn.zjj.mkcsserver.handler;

import cn.zjj.mkcsserver.auth.AuthenticatedUser;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsmodel.entity.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class MyMetaObjectHandlerTest {

    private final MyMetaObjectHandler handler = new MyMetaObjectHandler();

    @Test
    void auditFieldAnnotationsDeclareExpectedFillModes() throws NoSuchFieldException {
        assertFillMode("createdAt", FieldFill.INSERT);
        assertFillMode("updatedAt", FieldFill.INSERT_UPDATE);
        assertFillMode("createdBy", FieldFill.INSERT);
        assertFillMode("updatedBy", FieldFill.INSERT_UPDATE);
    }

    @Test
    void insertFillUsesMetaObjectReflectionAndCurrentLoginUser() {
        initializeTableInfo();
        Users user = new Users();
        MetaObject metaObject = SystemMetaObject.forObject(user);

        UserContext.set(new AuthenticatedUser(42L, "audit-user", 1L));
        try {
            handler.insertFill(metaObject);
        } finally {
            UserContext.clear();
        }

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getCreatedBy()).isEqualTo(42L);
        assertThat(user.getUpdatedBy()).isEqualTo(42L);
    }

    @Test
    void updateFillLeavesActorEmptyWithoutAnAuthenticatedUser() {
        initializeTableInfo();
        Users user = new Users();
        MetaObject metaObject = SystemMetaObject.forObject(user);

        handler.updateFill(metaObject);

        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getUpdatedBy()).isNull();
    }

    private void assertFillMode(String fieldName, FieldFill expectedFill) throws NoSuchFieldException {
        Field field = BaseEntity.class.getDeclaredField(fieldName);
        TableField annotation = field.getAnnotation(TableField.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.fill()).isEqualTo(expectedFill);
    }

    private void initializeTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "audit-test");
        assistant.setCurrentNamespace("audit-test");
        TableInfoHelper.initTableInfo(assistant, Users.class);
    }
}
