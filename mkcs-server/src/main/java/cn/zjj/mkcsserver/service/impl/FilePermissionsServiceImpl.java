package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FilePermissions;
import cn.zjj.mkcsserver.mapper.FilePermissionsMapper;
import cn.zjj.mkcsserver.service.FilePermissionsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 文件/文件夹权限控制表 (ACL) - 支持继承、分享、角色、部门等 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
public class FilePermissionsServiceImpl extends ServiceImpl<FilePermissionsMapper, FilePermissions> implements FilePermissionsService {

}
