package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionReportResultDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionReportResultMapper extends BaseMapperX<InspectionReportResultDO> {
    default List<InspectionReportResultDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionReportResultDO>()
                .eq(InspectionReportResultDO::getTenantId, query.tenantId())
                .eq(InspectionReportResultDO::getTaskId, query.taskId()).orderByAsc(InspectionReportResultDO::getId));
    }
}
