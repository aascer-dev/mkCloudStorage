package cn.zjj.mkcsserver.mapper;

import cn.zjj.mkcsmodel.entity.FilePermissions;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 文件/文件夹权限控制表 (ACL) - 支持继承、分享、角色、部门等 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Mapper
public interface FilePermissionsMapper extends BaseMapper<FilePermissions> {

}
