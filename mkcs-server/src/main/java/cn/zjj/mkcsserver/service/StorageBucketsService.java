package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.StorageBuckets;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 存储桶管理表 服务类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
public interface StorageBucketsService extends IService<StorageBuckets> {

    /**
     * 创建存储桶
     * @param bucketName 存储桶名称
     * @param description 描述
     * @param userId 用户ID
     * @return 创建的存储桶
     */
    StorageBuckets createBucket(String bucketName, String description, Long userId);

    /**
     * 删除存储桶
     * @param bucketId 存储桶ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteBucket(Long bucketId, Long userId);

    /**
     * 根据用户ID获取存储桶列表
     * @param userId 用户ID
     * @return 存储桶列表
     */
    List<StorageBuckets> getBucketsByUserId(Long userId);

    /**
     * 分页查询存储桶
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param userId 用户ID
     * @param bucketName 存储桶名称（模糊查询）
     * @param status 状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分页结果
     */
    IPage<StorageBuckets> getBucketPage(int pageNum, int pageSize, 
                                       Long userId, String bucketName, 
                                       Byte status, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 检查存储桶名称是否可用
     * @param bucketName 存储桶名称
     * @param excludeBucketId 排除的存储桶ID
     * @return 是否可用
     */
    boolean isBucketNameAvailable(String bucketName, Long excludeBucketId);

    /**
     * 根据存储桶名称获取存储桶
     * @param bucketName 存储桶名称
     * @return 存储桶信息
     */
    StorageBuckets getBucketByName(String bucketName);

    /**
     * 更新存储桶信息
     * @param bucket 存储桶信息
     * @return 更新后的存储桶
     */
    StorageBuckets updateBucket(StorageBuckets bucket);

    /**
     * 批量更新存储桶状态
     * @param bucketIds 存储桶ID列表
     * @param status 状态
     * @return 是否成功
     */
    boolean batchUpdateStatus(List<Long> bucketIds, Byte status);

    /**
     * 获取用户的默认存储桶
     * @param userId 用户ID
     * @return 默认存储桶
     */
    StorageBuckets getDefaultBucket(Long userId);

    /**
     * 设置用户的默认存储桶
     * @param userId 用户ID
     * @param bucketId 存储桶ID
     * @return 是否设置成功
     */
    boolean setDefaultBucket(Long userId, Long bucketId);

    /**
     * 统计用户的存储桶数量
     * @param userId 用户ID
     * @return 存储桶数量
     */
    Long countBucketsByUserId(Long userId);

    /**
     * 获取最近创建的存储桶
     * @param limit 限制数量
     * @return 存储桶列表
     */
    List<StorageBuckets> getRecentBuckets(int limit);

    /**
     * 同步MinIO存储桶到数据库
     * @return 同步的存储桶数量
     */
    int syncBucketsFromMinIO();
}