package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.FileContents;
import cn.zjj.mkcsserver.mapper.FileContentsMapper;
import cn.zjj.mkcsserver.service.FileContentsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 文件内容服务实现类
 * @author 34978
 */
@Service
public class FileContentsServiceImpl extends ServiceImpl<FileContentsMapper, FileContents> implements FileContentsService {

    /**
     * 根据内容hash获取文件内容
     */
    @Override
    public FileContents getByContentHash(String contentHash) {
        LambdaQueryWrapper<FileContents> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileContents::getContentHash, contentHash)
                .eq(FileContents::getStatus, 1);
        return baseMapper.selectOne(wrapper);
    }
}
