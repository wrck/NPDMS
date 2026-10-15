package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionReportRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionReportRevisionMapper extends BaseMapperX<InspectionReportRevisionDO> {
    default List<InspectionReportRevisionDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionReportRevisionDO>()
                .eq(InspectionReportRevisionDO::getTenantId, query.tenantId())
                .eq(InspectionReportRevisionDO::getTaskId, query.taskId()).orderByAsc(InspectionReportRevisionDO::getId));
    }
}
