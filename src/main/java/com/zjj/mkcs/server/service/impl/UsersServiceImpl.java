package com.zjj.mkcs.server.service.impl;

import com.zjj.mkcs.pojo.entity.Users;
import com.zjj.mkcs.server.mapper.UsersMapper;
import com.zjj.mkcs.server.service.UsersService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户核心表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {

}
