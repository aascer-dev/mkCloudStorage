package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.RolePermissions;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 角色-权限关联表 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface RolePermissionsMapper extends BaseMapper<RolePermissions> {

}
