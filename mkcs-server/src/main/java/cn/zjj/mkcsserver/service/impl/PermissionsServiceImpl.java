package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.Permissions;
import cn.zjj.mkcsserver.mapper.PermissionsMapper;
import cn.zjj.mkcsserver.service.PermissionsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 权限点定义表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
public class PermissionsServiceImpl extends ServiceImpl<PermissionsMapper, Permissions> implements PermissionsService {

}
