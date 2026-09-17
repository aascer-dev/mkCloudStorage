package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.dto.CreateShareRequest;
import cn.zjj.mkcsmodel.vo.ShareResponse;
import cn.zjj.mkcsserver.auth.UserContext;
import cn.zjj.mkcsserver.service.SharesService;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 文件/文件夹分享记录表 前端控制器
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
@Tag(name = "文件分享", description = "创建、管理和撤销链接分享")
public class SharesController {

    private final SharesService sharesService;

    @PostMapping
    @Operation(summary = "创建链接分享", description = "创建当前用户文件或文件夹的只读链接分享")
    public Result<ShareResponse> createShare(@Valid @RequestBody CreateShareRequest request) {
        return Result.success("分享链接已创建", sharesService.createLinkShare(UserContext.requireUserId(), request));
    }

    @GetMapping
    @Operation(summary = "获取我的分享", description = "获取当前用户创建的链接分享")
    public Result<List<ShareResponse>> getMyShares() {
        return Result.success("获取我的分享成功", sharesService.getMyShares(UserContext.requireUserId()));
    }

    @DeleteMapping("/{shareId}")
    @Operation(summary = "撤销分享", description = "撤销当前用户创建的链接分享")
    public Result<Void> revokeShare(@PathVariable Long shareId) {
        sharesService.revokeShare(UserContext.requireUserId(), shareId);
        return Result.success("分享已撤销", null);
    }
}
