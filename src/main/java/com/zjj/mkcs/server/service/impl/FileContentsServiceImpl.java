package com.zjj.mkcs.server.service.impl;

import com.zjj.mkcs.pojo.entity.FileContents;
import com.zjj.mkcs.server.mapper.FileContentsMapper;
import com.zjj.mkcs.server.service.FileContentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 文件内容去重表 - 相同内容只存一份 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class FileContentsServiceImpl extends ServiceImpl<FileContentsMapper, FileContents> implements FileContentsService {

}
