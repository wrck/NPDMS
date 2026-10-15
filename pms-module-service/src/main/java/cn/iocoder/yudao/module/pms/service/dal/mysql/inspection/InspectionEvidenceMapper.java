package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionEvidenceDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionEvidenceMapper extends BaseMapperX<InspectionEvidenceDO> {
    default List<InspectionEvidenceDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionEvidenceDO>()
                .eq(InspectionEvidenceDO::getTenantId, query.tenantId())
                .eq(InspectionEvidenceDO::getTaskId, query.taskId()).orderByAsc(InspectionEvidenceDO::getId));
    }
}
