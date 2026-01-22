package com.zjj.mkcs.server.service.impl;

import com.zjj.mkcs.pojo.entity.UploadTaskChunks;
import com.zjj.mkcs.server.mapper.UploadTaskChunksMapper;
import com.zjj.mkcs.server.service.UploadTaskChunksService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 上传任务分片明细表（断点续传核心） 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class UploadTaskChunksServiceImpl extends ServiceImpl<UploadTaskChunksMapper, UploadTaskChunks> implements UploadTaskChunksService {

}
