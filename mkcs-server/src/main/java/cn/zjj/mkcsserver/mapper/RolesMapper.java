package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.Roles;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 角色定义表 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface RolesMapper extends BaseMapper<Roles> {

    default Roles selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<Roles>()
                .eq(Roles::getName, name)
                .last("LIMIT 1"));
    }
}
