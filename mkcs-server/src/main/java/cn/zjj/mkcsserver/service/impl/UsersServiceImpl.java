package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.Users;
import cn.zjj.mkcsserver.mapper.UsersMapper;
import cn.zjj.mkcsserver.service.UsersService;
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
