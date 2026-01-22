package com.zjj.mkcs.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjj.mkcs.pojo.entity.Files;

/**
 * <p>
 * 文件元数据表 - 文件与文件夹记录，支持同目录文件名唯一 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
public interface FilesMapper extends BaseMapper<Files> {

}
