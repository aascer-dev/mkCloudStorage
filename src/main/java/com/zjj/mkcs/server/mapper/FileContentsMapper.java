package com.zjj.mkcs.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjj.mkcs.pojo.entity.FileContents;

/**
 * <p>
 * 文件内容去重表 - 相同内容只存一份 Mapper 接口
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
public interface FileContentsMapper extends BaseMapper<FileContents> {

}
