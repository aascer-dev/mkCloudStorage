package cn.zjj.mkcsserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.zjj.mkcsmodel.entity.UploadTasks;

/**
 * <p>
 * 上传/传输任务表 - 支持断点续传和任务管理 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
public interface UploadTasksMapper extends BaseMapper<UploadTasks> {

}
