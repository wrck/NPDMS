package cn.iocoder.yudao.module.pms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批历史 Mapper。
 */
@Mapper
public interface ApprovalHistoryMapper extends BaseMapper<ApprovalHistory> {
}
