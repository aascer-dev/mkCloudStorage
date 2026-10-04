package cn.zjj.mkcsserver.service.impl;


import cn.zjj.mkcsmodel.entity.Roles;
import cn.zjj.mkcsserver.mapper.RolesMapper;
import cn.zjj.mkcsserver.service.RolesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 角色定义表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
public class RolesServiceImpl extends ServiceImpl<RolesMapper, Roles> implements RolesService {

}
