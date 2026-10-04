package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.UploadTaskChunks;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 上传任务分片明细表（断点续传核心） Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface UploadTaskChunksMapper extends BaseMapper<UploadTaskChunks> {

}
