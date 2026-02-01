package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsserver.mapper.StorageBucketsMapper;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import cn.zjj.mkcsmodel.entity.StorageBuckets;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjj.mkcscommon.Assert;
import com.zjj.mkcscommon.result.BusinessException;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.utils.MinIOUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 存储桶管理表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageBucketsServiceImpl extends ServiceImpl<StorageBucketsMapper, StorageBuckets> implements StorageBucketsService {

    private final MinIOUtil minIOUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageBuckets createBucket(String bucketName, String description, Long userId) {
        Assert.notNull(userId, "用户ID不能为空");
        Assert.hasText(bucketName, "存储桶名称不能为空");
        
        // 验证存储桶名称格式（MinIO要求）
        validateBucketName(bucketName);
        
        // 检查存储桶名称是否已存在
        if (!isBucketNameAvailable(bucketName, null)) {
            throw new BusinessException(ResultCode.BUCKET_NAME_EXISTS);
        }
        
        // 在MinIO中创建存储桶
        boolean created = minIOUtil.createBucket(bucketName);
        if (!created) {
            throw new BusinessException(ResultCode.BUCKET_CREATE_FAILED);
        }
        
        try {
            // 在数据库中创建记录
            StorageBuckets bucket = new StorageBuckets();
            bucket.setOwnerId(userId);
            bucket.setName(bucketName);
            bucket.setDescription(description);
            bucket.setStatus((byte) 1);
            bucket.setTotalStorage(10737418240L);
            
            save(bucket);
            log.info("存储桶创建成功：用户ID={}, 存储桶名称={}", userId, bucketName);
            return bucket;
            
        } catch (Exception e) {
            // 如果数据库操作失败，删除MinIO中的存储桶
            log.error("数据库操作失败，回滚MinIO存储桶：{}", bucketName);
            minIOUtil.deleteBucket(bucketName);
            throw new BusinessException(ResultCode.BUCKET_CREATE_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBucket(Long bucketId, Long userId) {
        Assert.notNull(bucketId, "存储桶ID不能为空");
        Assert.notNull(userId, "用户ID不能为空");
        
        // 查询存储桶信息
        StorageBuckets bucket = getById(bucketId);
        if (bucket == null) {
            throw new BusinessException(ResultCode.BUCKET_NOT_FOUND);
        }
        
        // 检查权限
        if (!bucket.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }
        
        // 删除MinIO中的存储桶
        boolean deleted = minIOUtil.deleteBucket(bucket.getName());
        if (!deleted) {
            throw new BusinessException(ResultCode.BUCKET_DELETE_FAILED);
        }
        
        // 删除数据库记录
        boolean result = removeById(bucketId);
        if (result) {
            log.info("存储桶删除成功：用户ID={}, 存储桶ID={}, 存储桶名称={}", 
                    userId, bucketId, bucket.getName());
        }
        
        return result;
    }

    @Override
    public List<StorageBuckets> getBucketsByUserId(Long userId) {
        Assert.notNull(userId, "用户ID不能为空");
        
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageBuckets::getOwnerId, userId)
               .eq(StorageBuckets::getStatus, 1)
               .orderByDesc(StorageBuckets::getCreatedAt);
               
        return list(wrapper);
    }

    @Override
    public IPage<StorageBuckets> getBucketPage(int pageNum, int pageSize, 
                                              Long userId, String bucketName, 
                                              Byte status, LocalDateTime startTime, LocalDateTime endTime) {
        Page<StorageBuckets> page = new Page<>(pageNum, pageSize);
        
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        
        if (userId != null) {
            wrapper.eq(StorageBuckets::getOwnerId, userId);
        }
        
        if (StringUtils.hasText(bucketName)) {
            wrapper.like(StorageBuckets::getName, bucketName);
        }
        
        if (status != null) {
            wrapper.eq(StorageBuckets::getStatus, status);
        }
        
        if (startTime != null) {
            wrapper.ge(StorageBuckets::getCreatedAt, startTime);
        }
        
        if (endTime != null) {
            wrapper.le(StorageBuckets::getCreatedAt, endTime);
        }
        
        wrapper.orderByDesc(StorageBuckets::getCreatedAt);
        
        return page(page, wrapper);
    }

    @Override
    public boolean isBucketNameAvailable(String bucketName, Long excludeBucketId) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageBuckets::getName, bucketName);
        
        if (excludeBucketId != null) {
            wrapper.ne(StorageBuckets::getId, excludeBucketId);
        }
        
        return count(wrapper) == 0;
    }

    @Override
    public StorageBuckets getBucketByName(String bucketName) {
        Assert.hasText(bucketName, "存储桶名称不能为空");
        
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageBuckets::getName, bucketName)
               .eq(StorageBuckets::getStatus, 1);
               
        return getOne(wrapper);
    }

    @Override
    public StorageBuckets updateBucket(StorageBuckets bucket) {
        Assert.notNull(bucket, "存储桶信息不能为空");
        Assert.notNull(bucket.getId(), "存储桶ID不能为空");
        
        // 如果更新存储桶名称，需要检查可用性
        if (StringUtils.hasText(bucket.getName())) {
            validateBucketName(bucket.getName());
            if (!isBucketNameAvailable(bucket.getName(), bucket.getId())) {
                throw new BusinessException(ResultCode.BUCKET_NAME_EXISTS);
            }
        }
        
        boolean updated = updateById(bucket);
        if (updated) {
            log.info("存储桶更新成功：存储桶ID={}", bucket.getId());
            return getById(bucket.getId());
        }
        
        throw new BusinessException(ResultCode.BUCKET_UPDATE_FAILED);
    }

    @Override
    public boolean batchUpdateStatus(List<Long> bucketIds, Byte status) {
        Assert.notEmpty(bucketIds, "存储桶ID列表不能为空");
        Assert.notNull(status, "状态不能为空");
        
        StorageBuckets updateEntity = new StorageBuckets();
        updateEntity.setStatus(status);
        
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(StorageBuckets::getId, bucketIds);
        
        boolean result = update(updateEntity, wrapper);
        if (result) {
            log.info("批量更新存储桶状态成功：存储桶数量={}, 状态={}", bucketIds.size(), status);
        }
        
        return result;
    }

    @Override
    public StorageBuckets getDefaultBucket(Long userId) {
        // 当前实体无默认桶字段，返回最新创建的一个（示例实现）
        Assert.notNull(userId, "用户ID不能为空");
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageBuckets::getOwnerId, userId)
               .eq(StorageBuckets::getStatus, 1)
               .orderByDesc(StorageBuckets::getCreatedAt)
               .last("LIMIT 1");
        List<StorageBuckets> list = list(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultBucket(Long userId, Long bucketId) {
        // 当前实体无默认桶字段，示例返回失败或抛出不支持异常
        throw new BusinessException(ResultCode.BUSINESS_ERROR);
    }

    @Override
    public Long countBucketsByUserId(Long userId) {
        Assert.notNull(userId, "用户ID不能为空");
        
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageBuckets::getOwnerId, userId)
               .eq(StorageBuckets::getStatus, 1);
               
        return count(wrapper);
    }

    @Override
    public List<StorageBuckets> getRecentBuckets(int limit) {
        LambdaQueryWrapper<StorageBuckets> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageBuckets::getStatus, 1)
               .orderByDesc(StorageBuckets::getCreatedAt)
               .last("LIMIT " + limit);
               
        return list(wrapper);
    }

    @Override
    public int syncBucketsFromMinIO() {
        try {
            // 获取MinIO中的所有存储桶
            List<String> minioBuckets = minIOUtil.listBuckets();
            
            // 获取数据库中的所有存储桶
            List<StorageBuckets> dbBuckets = list();
            
            int syncCount = 0;
            
            // 同步MinIO中存在但数据库中不存在的存储桶
            for (String bucketName : minioBuckets) {
                boolean existsInDb = dbBuckets.stream()
                        .anyMatch(bucket -> bucket.getName().equals(bucketName));
                        
                if (!existsInDb) {
                    StorageBuckets bucket = new StorageBuckets();
                    bucket.setName(bucketName);
                    bucket.setDescription("从MinIO同步的存储桶");
                    bucket.setOwnerId(1L);
                    bucket.setStatus((byte) 1);
                    
                    save(bucket);
                    syncCount++;
                    log.info("同步存储桶到数据库：{}", bucketName);
                }
            }
            
            log.info("存储桶同步完成，同步数量：{}", syncCount);
            return syncCount;
            
        } catch (Exception e) {
            log.error("同步存储桶失败：{}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 验证存储桶名称格式
     * @param bucketName 存储桶名称
     */
    private void validateBucketName(String bucketName) {
        // MinIO存储桶名称规则：
        // 1. 长度在3-63个字符之间
        // 2. 只能包含小写字母、数字和连字符
        // 3. 必须以字母或数字开头和结尾
        // 4. 不能包含连续的连字符
        
        if (bucketName.length() < 3 || bucketName.length() > 63) {
            throw new BusinessException(ResultCode.INVALID_BUCKET_NAME);
        }
        
        if (!bucketName.matches("^[a-z0-9][a-z0-9-]*[a-z0-9]$") ) {
            throw new BusinessException(ResultCode.INVALID_BUCKET_NAME);
        }
        
        if (bucketName.contains("--")) {
            throw new BusinessException(ResultCode.INVALID_BUCKET_NAME);
        }
    }
}