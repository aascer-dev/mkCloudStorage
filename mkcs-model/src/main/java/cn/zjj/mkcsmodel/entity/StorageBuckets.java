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
 * 存储桶 - 容量与归属的核心表
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("storage_buckets")
public class StorageBuckets extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 拥有者用户ID（创建者/付费主体）
     */
    @TableField("owner_id")
    private Long ownerId;

    /**
     * 桶类型：0=个人私有, 1=团队/共享
     */
    @TableField("bucket_type")
    private Byte bucketType;

    /**
     * 桶名称（用户可见）
     */
    @TableField("name")
    private String name;

    /**
     * 桶描述，可选
     */
    @TableField("description")
    private String description;

    /**
     * 总配额（字节）
     */
    @TableField("total_storage")
    private Long totalStorage;

    /**
     * 已使用量（字节）
     */
    @TableField("used_storage")
    private Long usedStorage;

    /**
     * 状态：0=禁用, 1=正常, 2=只读（扩展用）
     */
    @TableField("status")
    private Byte status;
}
