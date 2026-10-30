package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionRuleFeedbackDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionRuleFeedbackMapper extends BaseMapperX<InspectionRuleFeedbackDO> {
    default List<InspectionRuleFeedbackDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionRuleFeedbackDO>()
                .eq(InspectionRuleFeedbackDO::getTenantId, query.tenantId())
                .eq(InspectionRuleFeedbackDO::getTaskId, query.taskId()).orderByAsc(InspectionRuleFeedbackDO::getId));
    }
}
