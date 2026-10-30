package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionAttemptDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionAttemptMapper extends BaseMapperX<InspectionAttemptDO> {
    default List<InspectionAttemptDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionAttemptDO>()
                .eq(InspectionAttemptDO::getTenantId, query.tenantId())
                .eq(InspectionAttemptDO::getTaskId, query.taskId()).orderByAsc(InspectionAttemptDO::getId));
    }
}
