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
 * 上传/传输任务表 - 支持断点续传和任务管理
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("upload_tasks")
public class UploadTasks implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 上传用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 目标存储桶ID
     */
    @TableField("bucket_id")
    private Long bucketId;

    /**
     * 目标父文件夹ID（NULL=根目录）
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 文件名（原始名）
     */
    @TableField("filename")
    private String filename;

    /**
     * 文件总大小（字节）
     */
    @TableField("total_size")
    private Long totalSize;

    /**
     * 已上传大小（字节）
     */
    @TableField("uploaded_size")
    private Long uploadedSize;

    /**
     * 分片大小（字节），NULL=未分片或小文件
     */
    @TableField("chunk_size")
    private Integer chunkSize;

    /**
     * 总分片数
     */
    @TableField("total_chunks")
    private Integer totalChunks;

    /**
     * 已上传分片数
     */
    @TableField("uploaded_chunks")
    private Integer uploadedChunks;

    /**
     * 任务类型：1=上传, 2=下载（扩展用）
     */
    @TableField("task_type")
    private Byte taskType;

    /**
     * 状态：0=等待中, 1=进行中, 2=暂停, 3=完成, 4=失败, 5=取消
     */
    @TableField("status")
    private Byte status;

    /**
     * 失败原因（可选）
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 文件整体 sha256（用于秒传/去重判断）
     */
    @TableField("file_hash")
    private String fileHash;

    /**
     * 临时存储路径（分片临时目录或对象存储临时key前缀）
     */
    @TableField("temp_path")
    private String tempPath;

    /**
     * 完成后关联的 files.id（成功后填写）
     */
    @TableField("final_file_id")
    private Long finalFileId;

    /**
     * 任务创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 最后更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 任务过期时间（未完成可自动清理）
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;
}
