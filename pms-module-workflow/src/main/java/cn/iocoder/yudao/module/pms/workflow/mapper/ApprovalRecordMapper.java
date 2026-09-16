package cn.iocoder.yudao.module.pms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一审批记录 Mapper。
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {
}
