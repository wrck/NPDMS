package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionFeedbackConfirmationDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionFeedbackConfirmationMapper extends BaseMapperX<InspectionFeedbackConfirmationDO> {
    List<InspectionFeedbackConfirmationDO> selectForTaskForUpdate(@Param("query") InspectionTaskRows query);
    default List<InspectionFeedbackConfirmationDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionFeedbackConfirmationDO>()
                .eq(InspectionFeedbackConfirmationDO::getTenantId, query.tenantId())
                .eq(InspectionFeedbackConfirmationDO::getTaskId, query.taskId()).orderByAsc(InspectionFeedbackConfirmationDO::getId));
    }
}
