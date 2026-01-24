package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.UploadTasks;
import cn.zjj.mkcsserver.mapper.UploadTasksMapper;
import cn.zjj.mkcsserver.service.UploadTasksService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 上传/传输任务表 - 支持断点续传和任务管理 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class UploadTasksServiceImpl extends ServiceImpl<UploadTasksMapper, UploadTasks> implements UploadTasksService {

}
