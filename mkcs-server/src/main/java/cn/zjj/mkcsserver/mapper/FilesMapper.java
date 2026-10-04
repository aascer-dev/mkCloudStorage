package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.Files;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface FilesMapper extends BaseMapper<Files> {

}
