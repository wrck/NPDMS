package cn.iocoder.yudao.module.pms.lowcode.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeApprovalChain;
import org.apache.ibatis.annotations.Mapper;

/**
 * 低代码发布多级审批链 Mapper。
 */
@Mapper
public interface LowCodeApprovalChainMapper extends BaseMapper<LowCodeApprovalChain> {
}
