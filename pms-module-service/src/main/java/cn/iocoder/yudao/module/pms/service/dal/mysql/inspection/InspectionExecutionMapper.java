package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionExecutionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionExecutionMapper extends BaseMapperX<InspectionExecutionDO> {
    default List<InspectionExecutionDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionExecutionDO>()
                .eq(InspectionExecutionDO::getTenantId, query.tenantId())
                .eq(InspectionExecutionDO::getTaskId, query.taskId()).orderByAsc(InspectionExecutionDO::getId));
    }
}
