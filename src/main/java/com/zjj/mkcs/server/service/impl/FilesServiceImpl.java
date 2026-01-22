package com.zjj.mkcs.server.service.impl;

import com.zjj.mkcs.pojo.entity.Files;
import com.zjj.mkcs.server.mapper.FilesMapper;
import com.zjj.mkcs.server.service.FilesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class FilesServiceImpl extends ServiceImpl<FilesMapper, Files> implements FilesService {

}
