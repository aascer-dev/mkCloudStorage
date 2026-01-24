package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.OauthIdentities;
import cn.zjj.mkcsserver.mapper.OauthIdentitiesMapper;
import cn.zjj.mkcsserver.service.OauthIdentitiesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 第三方身份关联表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class OauthIdentitiesServiceImpl extends ServiceImpl<OauthIdentitiesMapper, OauthIdentities> implements OauthIdentitiesService {

}
