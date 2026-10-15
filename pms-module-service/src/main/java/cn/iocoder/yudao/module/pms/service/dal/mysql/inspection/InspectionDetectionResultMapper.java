package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionDetectionResultDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionDetectionResultMapper extends BaseMapperX<InspectionDetectionResultDO> {
    default List<InspectionDetectionResultDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionDetectionResultDO>()
                .eq(InspectionDetectionResultDO::getTenantId, query.tenantId())
                .eq(InspectionDetectionResultDO::getTaskId, query.taskId()).orderByAsc(InspectionDetectionResultDO::getId));
    }
    List<InspectionDetectionResultDO> selectCurrent(@Param("query") InspectionAttemptRows query);
}
