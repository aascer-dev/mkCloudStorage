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
 * 文件内容去重表 - 相同内容只存一份
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Getter
@Setter
@ToString
@TableName("file_contents")
public class FileContents extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文件内容 sha256 hash（64位十六进制）
     */
    @TableField("content_hash")
    private String contentHash;

    /**
     * 文件大小（字节）
     */
    @TableField("size")
    private Long size;

    /**
     * 实际存储路径（对象存储 key 或本地路径）
     */
    @TableField("storage_path")
    private String storagePath;

    /**
     * MIME 类型
     */
    @TableField("mime_type")
    private String mimeType;

    /**
     * 状态：0=已删除/失效, 1=正常可用
     */
    @TableField("status")
    private Byte status;

    /**
     * 被引用的文件元数据记录数，用于安全删除判断
     */
    @TableField("reference_count")
    private Integer referenceCount;

    /**
     * 随机位置校验 - 起始字节位置（用于秒传安全验证）
     */
    @TableField("random_offset")
    private Long randomOffset;

    /**
     * 随机位置校验 - 字节长度（默认256KB）
     */
    @TableField("random_length")
    private Integer randomLength;

    /**
     * 随机位置校验 - 该位置数据的MD5 hash（用于秒传验证）
     */
    @TableField("random_position_hash")
    private String randomPositionHash;

}
