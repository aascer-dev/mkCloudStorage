package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.StorageBuckets;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 存储桶 - 容量与归属的核心表 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface StorageBucketsMapper extends BaseMapper<StorageBuckets> {

    /**
     * Atomically reserves logical file capacity. The affected-row count is zero
     * when the bucket is unavailable, does not belong to the user, or lacks
     * free capacity.
     */
    int reserveStorage(@Param("bucketId") Long bucketId, @Param("ownerId") Long ownerId, @Param("size") long size);

    /** Releases logical file capacity without allowing the counter below zero. */
    int releaseStorage(@Param("bucketId") Long bucketId, @Param("size") long size);
}
