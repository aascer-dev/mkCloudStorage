package cn.zjj.mkcsserver.service;

import cn.zjj.mkcsmodel.entity.FileContents;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 文件内容服务接口
 */
public interface FileContentsService extends IService<FileContents> {

    /**
     * 根据内容hash获取文件内容
     */
    FileContents getByContentHash(String contentHash);
}
