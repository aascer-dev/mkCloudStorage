package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.OauthIdentities;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 第三方身份关联表 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface OauthIdentitiesMapper extends BaseMapper<OauthIdentities> {

}
