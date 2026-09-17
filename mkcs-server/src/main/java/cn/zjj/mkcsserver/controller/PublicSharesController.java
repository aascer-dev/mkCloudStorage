package cn.zjj.mkcsserver.controller;

import cn.zjj.mkcsmodel.dto.PublicShareAccessRequest;
import cn.zjj.mkcsmodel.vo.PublicShareResponse;
import cn.zjj.mkcsserver.service.SharesService;
import com.zjj.mkcscommon.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/shares")
@RequiredArgsConstructor
@Tag(name = "公开分享", description = "无需登录即可验证并查看链接分享元数据")
public class PublicSharesController {

    private final SharesService sharesService;

    @PostMapping("/{shareCode}/access")
    @Operation(summary = "访问链接分享", description = "验证可选提取码后返回可公开展示的文件元数据")
    public Result<PublicShareResponse> accessShare(@PathVariable String shareCode,
                                                    @Valid @RequestBody(required = false) PublicShareAccessRequest request) {
        return Result.success("分享访问成功", sharesService.accessPublicShare(shareCode, request));
    }

}
