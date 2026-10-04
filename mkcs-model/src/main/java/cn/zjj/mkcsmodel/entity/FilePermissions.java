package cn.zjj.mkcsmodel.entity;

import cn.zjj.mkcsmodel.entity.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * <p>
 * 文件/文件夹权限控制表 (ACL) - 支持继承、分享、角色、部门等
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Getter
@Setter
@ToString
@TableName("file_permissions")
public class FilePermissions extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的文件/文件夹ID (files.id)
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 主体类型：1=用户, 2=角色, 3=部门/团队, 4=分享链接, 5=公开(所有人), 6=企业内所有成员, 7=匿名访问
     */
    @TableField("subject_type")
    private Byte subjectType;

    /**
     * 主体ID (用户ID/角色ID/部门ID/shares.id)，公开类型时可为NULL
     */
    @TableField("subject_id")
    private Long subjectId;

    /**
     * 权限位掩码 (位运算)
     */
    @TableField("perm_mask")
    private Integer permMask;

    /**
     * 是否继承父级权限: 0=显式权限(打破继承), 1=从父级继承
     */
    @TableField("is_inherited")
    private Boolean isInherited;

    /**
     * 继承来源的文件ID (优化查询)
     */
    @TableField("inherited_from")
    private Long inheritedFrom;

    /**
     * 权限过期时间 (NULL=永久有效)
     */
    @TableField("expired_at")
    private LocalDateTime expiredAt;

    /**
     * 状态: 0=已撤销/失效, 1=有效
     */
    @TableField("status")
    private Byte status;

    /**
     * 授予权限的用户ID (审计)
     */
    @TableField("granted_by")
    private Long grantedBy;

}
