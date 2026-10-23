package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionReportFileDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionReportFileMapper extends BaseMapperX<InspectionReportFileDO> {
    default List<InspectionReportFileDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionReportFileDO>()
                .eq(InspectionReportFileDO::getTenantId, query.tenantId())
                .eq(InspectionReportFileDO::getTaskId, query.taskId()).orderByAsc(InspectionReportFileDO::getId));
    }
}
