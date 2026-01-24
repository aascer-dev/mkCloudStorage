package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.UserRoles;
import cn.zjj.mkcsserver.mapper.UserRolesMapper;
import cn.zjj.mkcsserver.service.UserRolesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户-角色关联表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class UserRolesServiceImpl extends ServiceImpl<UserRolesMapper, UserRoles> implements UserRolesService {

}
