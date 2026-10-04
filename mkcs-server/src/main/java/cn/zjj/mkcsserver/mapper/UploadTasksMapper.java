package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.UploadTasks;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 上传/传输任务表 - 支持断点续传和任务管理 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface UploadTasksMapper extends BaseMapper<UploadTasks> {

}
