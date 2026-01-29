package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FileFavorites;
import cn.zjj.mkcsserver.mapper.FileFavoritesMapper;
import cn.zjj.mkcsserver.service.FileFavoritesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 文件/文件夹收藏表 - 用户星标/收藏功能 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
public class FileFavoritesServiceImpl extends ServiceImpl<FileFavoritesMapper, FileFavorites> implements FileFavoritesService {

}
