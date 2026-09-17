package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.Shares;
import cn.zjj.mkcsmodel.dto.CreateShareRequest;
import cn.zjj.mkcsmodel.dto.PublicShareAccessRequest;
import cn.zjj.mkcsmodel.vo.PublicShareResponse;
import cn.zjj.mkcsmodel.vo.ShareResponse;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 文件/文件夹分享记录表 服务类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
public interface SharesService extends IService<Shares> {

    ShareResponse createLinkShare(Long userId, CreateShareRequest request);

    List<ShareResponse> getMyShares(Long userId);

    void revokeShare(Long userId, Long shareId);

    PublicShareResponse accessPublicShare(String shareCode, PublicShareAccessRequest request);

}
