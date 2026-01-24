package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.StorageBuckets;
import cn.zjj.mkcsserver.mapper.StorageBucketsMapper;
import cn.zjj.mkcsserver.service.StorageBucketsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 存储桶 - 容量与归属的核心表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class StorageBucketsServiceImpl extends ServiceImpl<StorageBucketsMapper, StorageBuckets> implements StorageBucketsService {

}
