package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.FileFavorites;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 文件/文件夹收藏表 - 用户星标/收藏功能 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface FileFavoritesMapper extends BaseMapper<FileFavorites> {

}
