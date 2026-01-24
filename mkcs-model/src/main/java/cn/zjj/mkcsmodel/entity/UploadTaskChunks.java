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
 * 上传任务分片明细表（断点续传核心）
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("upload_task_chunks")
public class UploadTaskChunks implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属上传任务ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 分片序号（从0开始）
     */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /**
     * 本分片大小
     */
    @TableField("chunk_size")
    private Long chunkSize;

    /**
     * 已上传字节（支持部分上传）
     */
    @TableField("uploaded_size")
    private Long uploadedSize;

    /**
     * 0=未开始, 1=进行中, 2=完成, 3=失败
     */
    @TableField("status")
    private Byte status;

    /**
     * 对象存储返回的 ETag（用于合并验证）
     */
    @TableField("etag")
    private String etag;

    @TableField("updated_time")
    private LocalDateTime updatedTime;
}
