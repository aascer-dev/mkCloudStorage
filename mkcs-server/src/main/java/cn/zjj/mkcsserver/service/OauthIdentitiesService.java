package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.OauthIdentities;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * <p>
 * 第三方身份关联表 服务类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
public interface OauthIdentitiesService extends IService<OauthIdentities> {
    /**
     * 根据平台和标识查询OAuth身份
     * @param provider 平台标识
     * @param identifier 第三方平台的唯一ID
     * @return OAuth身份信息
     */
    OauthIdentities getByProviderAndIdentifier(
        String provider,
        String identifier
    );

    /**
     * 创建OAuth身份关联
     * @param userId 用户ID
     * @param provider 平台标识
     * @param identifier 第三方平台的唯一ID
     * @param credential 凭证信息
     * @return OAuth身份信息
     */
    OauthIdentities create(
        Long userId,
        String provider,
        String identifier,
        String credential
    );

    /**
     * 更新OAuth身份关联
     * @param userId 用户ID
     * @param provider 平台标识
     * @param identifier 第三方平台的唯一ID
     * @param credential 凭证信息
     * @return OAuth身份信息
     */
    OauthIdentities update(
        Long userId,
        String provider,
        String identifier,
        String credential
    );

    /**
     * 根据平台和用户ID查询OAuth身份
     * @param provider 平台标识
     * @param userId 用户ID
     * @return OAuth身份信息
     */
    OauthIdentities getByProviderAndUserId(String provider, Long userId);

    /**
     * 查询用户的所有OAuth绑定
     * @param userId 用户ID
     * @return OAuth绑定列表
     */
    List<OauthIdentities> listByUserId(Long userId);

    /**
     * 统计用户的OAuth绑定数量
     * @param userId 用户ID
     * @return 绑定数量
     */
    long countByUserId(Long userId);
}
