package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskEventDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionTaskEventMapper extends BaseMapperX<InspectionTaskEventDO> {
    default List<InspectionTaskEventDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionTaskEventDO>()
                .eq(InspectionTaskEventDO::getTenantId, query.tenantId())
                .eq(InspectionTaskEventDO::getTaskId, query.taskId()).orderByAsc(InspectionTaskEventDO::getId));
    }
}
