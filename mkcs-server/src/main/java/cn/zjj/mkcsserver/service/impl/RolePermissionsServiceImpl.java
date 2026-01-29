package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.RolePermissions;
import cn.zjj.mkcsserver.mapper.RolePermissionsMapper;
import cn.zjj.mkcsserver.service.RolePermissionsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 角色-权限关联表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
public class RolePermissionsServiceImpl extends ServiceImpl<RolePermissionsMapper, RolePermissions> implements RolePermissionsService {

}
