package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskRuleSnapshotDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionTaskRuleSnapshotMapper extends BaseMapperX<InspectionTaskRuleSnapshotDO> {
    default List<InspectionTaskRuleSnapshotDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionTaskRuleSnapshotDO>()
                .eq(InspectionTaskRuleSnapshotDO::getTenantId, query.tenantId())
                .eq(InspectionTaskRuleSnapshotDO::getTaskId, query.taskId()).orderByAsc(InspectionTaskRuleSnapshotDO::getId));
    }
}
