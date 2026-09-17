package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.FileFavorites;
import cn.zjj.mkcsmodel.vo.FavoriteFileResponse;
import cn.zjj.mkcsmodel.vo.FileSummaryResponse;
import cn.zjj.mkcsserver.mapper.FileFavoritesMapper;
import cn.zjj.mkcsserver.service.FileFavoritesService;
import cn.zjj.mkcsserver.service.FilesService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 文件/文件夹收藏表 - 用户星标/收藏功能 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
@RequiredArgsConstructor
public class FileFavoritesServiceImpl extends ServiceImpl<FileFavoritesMapper, FileFavorites> implements FileFavoritesService {

    private final FilesService filesService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FavoriteFileResponse favorite(Long userId, Long fileId, String notes) {
        Files file = filesService.getFileInfo(userId, fileId);
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        FileFavorites favorite = baseMapper.selectOne(new LambdaQueryWrapper<FileFavorites>()
                .eq(FileFavorites::getUserId, userId)
                .eq(FileFavorites::getFileId, fileId));
        if (favorite == null) {
            favorite = new FileFavorites();
            favorite.setUserId(userId);
            favorite.setFileId(fileId);
            favorite.setNotes(notes);
            favorite.setStatus((byte) 1);
            if (baseMapper.insert(favorite) != 1) {
                throw new IllegalStateException("收藏失败");
            }
        } else {
            favorite.setNotes(notes);
            favorite.setStatus((byte) 1);
            if (baseMapper.updateById(favorite) != 1) {
                throw new IllegalStateException("收藏失败");
            }
        }
        return toResponse(favorite, file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long userId, Long fileId) {
        FileFavorites favorite = baseMapper.selectOne(new LambdaQueryWrapper<FileFavorites>()
                .eq(FileFavorites::getUserId, userId)
                .eq(FileFavorites::getFileId, fileId)
                .eq(FileFavorites::getStatus, 1));
        if (favorite == null) {
            return;
        }
        favorite.setStatus((byte) 0);
        if (baseMapper.updateById(favorite) != 1) {
            throw new IllegalStateException("取消收藏失败");
        }
    }

    @Override
    public List<FavoriteFileResponse> getFavorites(Long userId) {
        List<FileFavorites> favorites = baseMapper.selectList(new LambdaQueryWrapper<FileFavorites>()
                .eq(FileFavorites::getUserId, userId)
                .eq(FileFavorites::getStatus, 1)
                .orderByDesc(FileFavorites::getUpdatedAt)
                .orderByDesc(FileFavorites::getId));
        List<FavoriteFileResponse> responses = new ArrayList<>();
        for (FileFavorites favorite : favorites) {
            Files file = filesService.getFileInfo(userId, favorite.getFileId());
            if (file != null) {
                responses.add(toResponse(favorite, file));
            }
        }
        return responses;
    }

    @Override
    public Set<Long> getFavoriteFileIds(Long userId) {
        return new HashSet<>(baseMapper.selectActiveFileIdsByUserId(userId));
    }

    private FavoriteFileResponse toResponse(FileFavorites favorite, Files file) {
        return FavoriteFileResponse.builder()
                .id(favorite.getId())
                .notes(favorite.getNotes())
                .createdAt(favorite.getCreatedAt())
                .updatedAt(favorite.getUpdatedAt())
                .file(FileSummaryResponse.from(file))
                .build();
    }
}
