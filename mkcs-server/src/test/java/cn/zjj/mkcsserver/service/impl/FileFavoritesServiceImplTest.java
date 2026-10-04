package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FileFavorites;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.vo.FavoriteFileResponse;
import cn.zjj.mkcsserver.mapper.FileFavoritesMapper;
import cn.zjj.mkcsserver.service.FilesService;
import com.zjj.mkcscommon.result.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class FileFavoritesServiceImplTest {

    @Mock
    private FileFavoritesMapper favoritesMapper;

    @Mock
    private FilesService filesService;

    private FileFavoritesServiceImpl favoritesService;

    @BeforeEach
    void setUp() {
        favoritesService = new FileFavoritesServiceImpl(filesService);
        ReflectionTestUtils.setField(favoritesService, "baseMapper", favoritesMapper);
    }

    @Test
    void favoriteRejectsAFileTheCallerDoesNotOwn() {
        when(filesService.getFileInfo(10L, 7L)).thenReturn(null);

        assertThatThrownBy(() -> favoritesService.favorite(10L, 7L, "work"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资源不存在");

        verifyNoInteractions(favoritesMapper);
    }

    @Test
    void favoriteCreatesAnActiveFavoriteForTheOwnedFile() {
        Files file = ownedFile(7L, 10L);
        when(filesService.getFileInfo(10L, 7L)).thenReturn(file);
        when(favoritesMapper.selectOne(any())).thenReturn(null);
        when(favoritesMapper.insert(any(FileFavorites.class))).thenAnswer(invocation -> {
            ((FileFavorites) invocation.getArgument(0)).setId(99L);
            return 1;
        });

        FavoriteFileResponse response = favoritesService.favorite(10L, 7L, "important");

        ArgumentCaptor<FileFavorites> favoriteCaptor = ArgumentCaptor.forClass(FileFavorites.class);
        verify(favoritesMapper).insert((FileFavorites) favoriteCaptor.capture());
        assertThat(favoriteCaptor.getValue().getUserId()).isEqualTo(10L);
        assertThat(favoriteCaptor.getValue().getFileId()).isEqualTo(7L);
        assertThat(favoriteCaptor.getValue().getStatus()).isEqualTo((byte) 1);
        assertThat(response.getFile().getFilename()).isEqualTo("report.pdf");
    }

    @Test
    void unfavoriteIsIdempotentWhenNoActiveFavoriteExists() {
        when(favoritesMapper.selectOne(any())).thenReturn(null);

        favoritesService.unfavorite(10L, 7L);

        verify(favoritesMapper, never()).updateById(any(FileFavorites.class));
    }

    @Test
    void getFavoriteFileIdsReturnsOnlyTheCurrentUsersActiveFavoriteIds() {
        when(favoritesMapper.selectActiveFileIdsByUserId(10L)).thenReturn(List.of(7L, 11L));

        assertThat(favoritesService.getFavoriteFileIds(10L)).containsExactlyInAnyOrder(7L, 11L);

        verify(favoritesMapper).selectActiveFileIdsByUserId(10L);
    }

    private Files ownedFile(Long id, Long ownerId) {
        Files file = new Files();
        file.setId(id);
        file.setOwnerId(ownerId);
        file.setBucketId(2L);
        file.setFilename("report.pdf");
        file.setIsFolder(false);
        file.setSize(1024L);
        return file;
    }
}
