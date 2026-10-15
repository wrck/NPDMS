package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.ServiceIssueDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ServiceIssueMapper extends BaseMapperX<ServiceIssueDO> {
    List<ServiceIssueDO> selectForTaskForUpdate(@Param("query") InspectionTaskRows query);
    List<cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionIssueGovernanceFact> selectForGovernance(
            @Param("query") InspectionProjectRows query);
    List<cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.ServiceIssueSummary> selectWorkbench(
            @Param("query") ServiceIssueWorkbenchQuery query);
    default List<ServiceIssueDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<ServiceIssueDO>()
                .eq(ServiceIssueDO::getTenantId, query.tenantId())
                .eq(ServiceIssueDO::getTaskId, query.taskId()).orderByAsc(ServiceIssueDO::getId));
    }
}
