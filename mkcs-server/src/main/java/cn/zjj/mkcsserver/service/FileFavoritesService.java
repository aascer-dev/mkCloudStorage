package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.FileFavorites;
import cn.zjj.mkcsmodel.vo.FavoriteFileResponse;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 文件/文件夹收藏表 - 用户星标/收藏功能 服务类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
public interface FileFavoritesService extends IService<FileFavorites> {

    FavoriteFileResponse favorite(Long userId, Long fileId, String notes);

    void unfavorite(Long userId, Long fileId);

    List<FavoriteFileResponse> getFavorites(Long userId);

    Set<Long> getFavoriteFileIds(Long userId);

}
