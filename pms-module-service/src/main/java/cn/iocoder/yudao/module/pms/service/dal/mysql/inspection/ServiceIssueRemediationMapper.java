package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.ServiceIssueRemediationDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ServiceIssueRemediationMapper extends BaseMapperX<ServiceIssueRemediationDO> {
    default List<ServiceIssueRemediationDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<ServiceIssueRemediationDO>()
                .eq(ServiceIssueRemediationDO::getTenantId, query.tenantId())
                .eq(ServiceIssueRemediationDO::getTaskId, query.taskId()).orderByAsc(ServiceIssueRemediationDO::getId));
    }
}
