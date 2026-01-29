package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.Files;
import cn.zjj.mkcsserver.mapper.FilesMapper;
import cn.zjj.mkcsserver.service.FilesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-27
 */
@Service
public class FilesServiceImpl extends ServiceImpl<FilesMapper, Files> implements FilesService {

}
