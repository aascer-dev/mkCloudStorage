package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.dto.CreateShareRequest;
import cn.zjj.mkcsmodel.dto.PublicShareAccessRequest;
import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsmodel.entity.Shares;
import cn.zjj.mkcsmodel.vo.FileSummaryResponse;
import cn.zjj.mkcsmodel.vo.PublicShareResponse;
import cn.zjj.mkcsmodel.vo.ShareResponse;
import cn.zjj.mkcsserver.mapper.SharesMapper;
import cn.zjj.mkcsserver.service.CryptoService;
import cn.zjj.mkcsserver.service.FileContentsService;
import cn.zjj.mkcsserver.service.FilesService;
import cn.zjj.mkcsserver.service.SharesService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjj.mkcscommon.enumeration.ResultCode;
import com.zjj.mkcscommon.result.BusinessException;
import com.zjj.mkcscommon.utils.MinIOUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 文件/文件夹分享记录表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
@RequiredArgsConstructor
public class SharesServiceImpl extends ServiceImpl<SharesMapper, Shares> implements SharesService {

    private static final byte LINK_SHARE = 1;
    private static final byte READ_ONLY = 1;
    private static final byte ACTIVE = 1;
    private static final byte REVOKED = 0;
    private static final byte EXPIRED = 2;
    private static final String FILES_BUCKET = "files";
    private static final int PUBLIC_DOWNLOAD_URL_TIMEOUT_SECONDS = 60;

    private final FilesService filesService;
    private final CryptoService cryptoService;
    private final FileContentsService fileContentsService;
    private final MinIOUtil minIOUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShareResponse createLinkShare(Long userId, CreateShareRequest request) {
        Files file = filesService.getFileInfo(userId, request.getFileId());
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        Shares share = new Shares();
        share.setShareType(LINK_SHARE);
        share.setSharerId(userId);
        share.setFileId(file.getId());
        share.setPermission(READ_ONLY);
        share.setShareLink(nextShareCode());
        share.setPassword(cryptoService.hashUserPassword(request.getPassword()));
        share.setExpiredAt(request.getExpiresAt() == null ? LocalDateTime.now().plusDays(1) : request.getExpiresAt());
        share.setStatus(ACTIVE);
        if (baseMapper.insert(share) != 1) {
            throw new IllegalStateException("创建分享失败");
        }
        return toResponse(share, file);
    }

    @Override
    public List<ShareResponse> getMyShares(Long userId) {
        List<Shares> shares = baseMapper.selectList(new LambdaQueryWrapper<Shares>()
                .eq(Shares::getSharerId, userId)
                .orderByDesc(Shares::getCreatedAt)
                .orderByDesc(Shares::getId));
        List<ShareResponse> responses = new ArrayList<>();
        for (Shares share : shares) {
            expireIfNeeded(share);
            responses.add(toResponse(share, filesService.getFileInfo(userId, share.getFileId())));
        }
        return responses;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeShare(Long userId, Long shareId) {
        Shares share = baseMapper.selectById(shareId);
        if (share == null || !userId.equals(share.getSharerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (share.getStatus() == ACTIVE) {
            share.setStatus(REVOKED);
            if (baseMapper.updateById(share) != 1) {
                throw new IllegalStateException("撤销分享失败");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublicShareResponse accessPublicShare(String shareCode, PublicShareAccessRequest request) {
        Shares share = requireActiveShare(shareCode);
        if (hasText(share.getPassword()) && (request == null || !cryptoService.verifyUserPassword(request.getPassword(), share.getPassword()))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "提取码错误");
        }
        Files file = filesService.getFileInfo(share.getSharerId(), share.getFileId());
        if (file == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        FileContents content = fileContentsService.getById(file.getContentId());
        if (content == null || content.getStatus() != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        MinIOUtil.ObjectLocation objectLocation = minIOUtil.parseStoragePath(content.getStoragePath(), FILES_BUCKET);
        return PublicShareResponse.builder()
                .shareCode(share.getShareLink())
                .passwordProtected(hasText(share.getPassword()))
                .expiresAt(share.getExpiredAt())
                .downloadUrl(minIOUtil.presignDownload(objectLocation.bucketName(), objectLocation.objectKey(), file.getFilename(),
                        Duration.ofSeconds(PUBLIC_DOWNLOAD_URL_TIMEOUT_SECONDS)))
                .downloadUrlExpiresInSeconds(PUBLIC_DOWNLOAD_URL_TIMEOUT_SECONDS)
                .file(FileSummaryResponse.from(file))
                .build();
    }

    private Shares requireActiveShare(String shareCode) {
        Shares share = baseMapper.selectOne(new LambdaQueryWrapper<Shares>()
                .eq(Shares::getShareLink, shareCode));
        if (share == null || share.getStatus() != ACTIVE) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        expireIfNeeded(share);
        if (share.getStatus() != ACTIVE) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return share;
    }

    private String nextShareCode() {
        for (int attempt = 0; attempt < 3; attempt++) {
            String code = cryptoService.generateSecureToken();
            Long count = baseMapper.selectCount(new LambdaQueryWrapper<Shares>().eq(Shares::getShareLink, code));
            if (count == 0) {
                return code;
            }
        }
        throw new IllegalStateException("生成分享链接失败");
    }

    private void expireIfNeeded(Shares share) {
        if (share.getStatus() == ACTIVE && share.getExpiredAt() != null && !share.getExpiredAt().isAfter(LocalDateTime.now())) {
            share.setStatus(EXPIRED);
            if (baseMapper.updateById(share) != 1) {
                throw new IllegalStateException("更新分享状态失败");
            }
        }
    }

    private ShareResponse toResponse(Shares share, Files file) {
        return ShareResponse.builder()
                .id(share.getId())
                .shareCode(share.getShareLink())
                .passwordProtected(hasText(share.getPassword()))
                .expiresAt(share.getExpiredAt())
                .status(share.getStatus())
                .createdAt(share.getCreatedAt())
                .file(file == null ? null : FileSummaryResponse.from(file))
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
