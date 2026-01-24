package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.FileContents;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 文件内容去重表 - 相同内容只存一份 服务类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
public interface FileContentsService extends IService<FileContents> {

}
