package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionAnnotationDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionAnnotationMapper extends BaseMapperX<InspectionAnnotationDO> {
    default List<InspectionAnnotationDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionAnnotationDO>()
                .eq(InspectionAnnotationDO::getTenantId, query.tenantId())
                .eq(InspectionAnnotationDO::getTaskId, query.taskId()).orderByAsc(InspectionAnnotationDO::getId));
    }
}
