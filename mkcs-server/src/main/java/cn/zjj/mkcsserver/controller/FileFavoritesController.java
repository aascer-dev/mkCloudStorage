package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.dto.FavoriteFileRequest;
import cn.zjj.mkcsmodel.vo.FavoriteFileResponse;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsserver.service.FileFavoritesService;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 文件/文件夹收藏表 - 用户星标/收藏功能 前端控制器
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "文件收藏", description = "管理当前用户的文件和文件夹收藏")
public class FileFavoritesController {

    private final FileFavoritesService fileFavoritesService;

    @PutMapping("/{fileId}")
    @Operation(summary = "收藏文件", description = "收藏当前用户拥有的文件或文件夹；重复请求会更新备注")
    public Result<FavoriteFileResponse> favorite(@PathVariable Long fileId,
                                                  @Valid @RequestBody FavoriteFileRequest request) {
        return Result.success("收藏成功", fileFavoritesService.favorite(UserContext.requireUserId(), fileId, request.getNotes()));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "取消收藏", description = "取消当前用户对指定文件或文件夹的收藏")
    public Result<Void> unfavorite(@PathVariable Long fileId) {
        fileFavoritesService.unfavorite(UserContext.requireUserId(), fileId);
        return Result.success("已取消收藏", null);
    }

    @GetMapping
    @Operation(summary = "获取收藏夹", description = "获取当前用户的有效收藏")
    public Result<List<FavoriteFileResponse>> getFavorites() {
        return Result.success("获取收藏夹成功", fileFavoritesService.getFavorites(UserContext.requireUserId()));
    }
}
