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
 * 文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("files")
public class Files implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属存储桶ID
     */
    @TableField("bucket_id")
    private Long bucketId;

    /**
     * 上传者/拥有者用户ID
     */
    @TableField("owner_id")
    private Long ownerId;

    /**
     * 关联的文件内容实体ID（文件夹时为 NULL）
     */
    @TableField("content_id")
    private Long contentId;

    /**
     * 文件名或文件夹名（用户可见）
     */
    @TableField("filename")
    private String filename;

    /**
     * 父文件夹ID，根目录为 NULL
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 是否文件夹：0=文件, 1=文件夹
     */
    @TableField("is_folder")
    private Boolean isFolder;

    /**
     * 文件大小（字节，文件夹为 0 或子项总和视需求）
     */
    @TableField("size")
    private Long size;

    /**
     * 完整路径（冗余字段，便于查询和显示，如 /folder1/sub/file.txt）
     */
    @TableField("path")
    private String path;

    /**
     * 状态：0=已删除（软删除）, 1=正常, 2=回收站（可选）
     */
    @TableField("status")
    private Byte status;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 最后一次访问/打开/预览/下载的时间，用于“最近使用”排序
     */
    @TableField("last_accessed_time")
    private LocalDateTime lastAccessedTime;
}
