package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.FileContents;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 文件内容去重表 - 相同内容只存一份 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface FileContentsMapper extends BaseMapper<FileContents> {

}
