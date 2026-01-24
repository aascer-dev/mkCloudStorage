package cn.zjj.mkcsmodel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 文件/文件夹分享记录表
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("shares")
public class Shares implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 分享类型：1=链接分享, 2=协作邀请（指定用户）, 3=团队共享
     */
    @TableField("share_type")
    private Byte shareType;

    /**
     * 分享者用户ID
     */
    @TableField("sharer_id")
    private Long sharerId;

    /**
     * 接收者用户ID（协作邀请时填写，链接分享可为空）
     */
    @TableField("receiver_id")
    private Long receiverId;

    /**
     * 被分享的文件/文件夹ID（files表id）
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 权限：1=只读（预览/下载）, 2=可下载+转存, 3=可编辑（协作）
     */
    @TableField("permission")
    private Byte permission;

    /**
     * 分享链接（链接分享时生成唯一码，如 /s/abc123）
     */
    @TableField("share_link")
    private String shareLink;

    /**
     * 提取码（可选）
     */
    @TableField("password")
    private String password;

    /**
     * 链接过期时间（NULL=永久）
     */
    @TableField("expired_at")
    private LocalDateTime expiredAt;

    /**
     * 状态：0=已取消/失效, 1=有效, 2=已过期
     */
    @TableField("status")
    private Byte status;

    /**
     * 分享时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
