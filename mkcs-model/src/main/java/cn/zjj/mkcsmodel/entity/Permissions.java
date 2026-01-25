package cn.zjj.mkcsmodel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 权限点定义表
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("permissions")
public class Permissions extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 唯一权限名，如 file:delete, sys:user:ban
     */
    @TableField("name")
    private String name;

    /**
     * 权限描述
     */
    @TableField("description")
    private String description;
}
