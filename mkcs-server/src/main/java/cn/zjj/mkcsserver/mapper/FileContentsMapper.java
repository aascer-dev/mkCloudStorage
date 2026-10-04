package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.FileContents;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件内容Mapper
 */
@Mapper
public interface FileContentsMapper extends BaseMapper<FileContents> {
}
