package com.zjj.mkcs.server.service.impl;

import com.zjj.mkcs.pojo.entity.UserRoles;
import com.zjj.mkcs.server.mapper.UserRolesMapper;
import com.zjj.mkcs.server.service.UserRolesService;
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
