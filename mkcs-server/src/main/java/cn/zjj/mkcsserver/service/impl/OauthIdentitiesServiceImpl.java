package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.OauthIdentities;
import cn.zjj.mkcsserver.mapper.OauthIdentitiesMapper;
import cn.zjj.mkcsserver.service.OauthIdentitiesService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 第三方身份关联表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
@Slf4j
public class OauthIdentitiesServiceImpl
    extends ServiceImpl<OauthIdentitiesMapper, OauthIdentities>
    implements OauthIdentitiesService
{

    @Override
    public OauthIdentities getByProviderAndIdentifier(
        String provider,
        String identifier
    ) {
        LambdaQueryWrapper<OauthIdentities> wrapper =
            new LambdaQueryWrapper<>();
        wrapper
            .eq(OauthIdentities::getProvider, provider)
            .eq(OauthIdentities::getIdentifier, identifier);
        return getOne(wrapper);
    }

    @Override
    public OauthIdentities create(
        Long userId,
        String provider,
        String identifier,
        String credential
    ) {
        // 创建新记录
        OauthIdentities oauthIdentity = new OauthIdentities();
        oauthIdentity.setUserId(userId);
        oauthIdentity.setProvider(provider);
        oauthIdentity.setIdentifier(identifier);
        oauthIdentity.setCredential(credential);
        save(oauthIdentity);
        log.info(
            "创建OAuth身份关联: userId={}, provider={}, identifier={}",
            userId,
            provider,
            identifier
        );
        return oauthIdentity;
    }

    @Override
    public OauthIdentities update(
        Long userId,
        String provider,
        String identifier,
        String credential
    ) {
        // 查询现有记录
        OauthIdentities existing = getByProviderAndIdentifier(
            provider,
            identifier
        );

        if (existing == null) {
            log.warn(
                "OAuth身份不存在，无法更新: provider={}, identifier={}",
                provider,
                identifier
            );
            throw new RuntimeException("OAuth身份不存在");
        }

        // 更新现有记录
        existing.setUserId(userId);
        existing.setCredential(credential);
        updateById(existing);
        log.info(
            "更新OAuth身份关联: userId={}, provider={}, identifier={}",
            userId,
            provider,
            identifier
        );
        return existing;
    }

    @Override
    public OauthIdentities getByProviderAndUserId(
        String provider,
        Long userId
    ) {
        LambdaQueryWrapper<OauthIdentities> wrapper =
            new LambdaQueryWrapper<>();
        wrapper
            .eq(OauthIdentities::getProvider, provider)
            .eq(OauthIdentities::getUserId, userId);
        return getOne(wrapper);
    }

    @Override
    public List<OauthIdentities> listByUserId(Long userId) {
        LambdaQueryWrapper<OauthIdentities> wrapper =
            new LambdaQueryWrapper<>();
        wrapper
            .eq(OauthIdentities::getUserId, userId)
            .orderByDesc(OauthIdentities::getCreatedAt);
        return list(wrapper);
    }

    @Override
    public long countByUserId(Long userId) {
        LambdaQueryWrapper<OauthIdentities> wrapper =
            new LambdaQueryWrapper<>();
        wrapper.eq(OauthIdentities::getUserId, userId);
        return count(wrapper);
    }
}
