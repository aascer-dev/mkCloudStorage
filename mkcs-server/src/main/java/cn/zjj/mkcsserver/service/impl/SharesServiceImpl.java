package cn.zjj.mkcsserver.service.impl;

import cn.zjj.mkcsmodel.entity.Shares;
import cn.zjj.mkcsserver.mapper.SharesMapper;
import cn.zjj.mkcsserver.service.SharesService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 文件/文件夹分享记录表 服务实现类
 * </p>
 *
 * @author zjj
 * @since 2026-01-22
 */
@Service
public class SharesServiceImpl extends ServiceImpl<SharesMapper, Shares> implements SharesService {

}
