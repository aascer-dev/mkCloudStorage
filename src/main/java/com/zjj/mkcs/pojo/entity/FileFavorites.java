package com.zjj.mkcs.pojo.entity;

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
 * 文件/文件夹收藏表 - 用户星标/收藏功能
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("file_favorites")
public class FileFavorites implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 收藏的用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 被收藏的文件或文件夹ID
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 收藏备注/标签（可选，用户自定义）
     */
    @TableField("notes")
    private String notes;

    /**
     * 状态：0=已取消收藏, 1=正常收藏
     */
    @TableField("status")
    private Byte status;

    /**
     * 收藏时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
