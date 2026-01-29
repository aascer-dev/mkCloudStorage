package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.Permissions;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 权限点定义表 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface PermissionsMapper extends BaseMapper<Permissions> {

}
