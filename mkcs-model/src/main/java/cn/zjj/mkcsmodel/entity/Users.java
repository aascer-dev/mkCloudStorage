package cn.zjj.mkcsmodel.entity;

import cn.zjj.mkcsmodel.entity.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * 用户核心表
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("users")
public class Users extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 当前默认使用的存储桶ID
     */
    @TableField("current_bucket_id")
    private Long currentBucketId;

    /**
     * 唯一内部系统登录名/标识
     */
    @TableField("username")
    private String username;

    /**
     * 显示昵称，允许重复
     */
    @TableField("nickname")
    private String nickname;

    /**
     * 加密存储，OAuth 用户可为空
     */
    @TableField("password")
    private String password;

    /**
     * 可选，不唯一
     */
    @TableField("email")
    private String email;

    /**
     * 头像
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 0: 禁用, 1: 正常
     */
    @TableField("status")
    private Byte status;
}
