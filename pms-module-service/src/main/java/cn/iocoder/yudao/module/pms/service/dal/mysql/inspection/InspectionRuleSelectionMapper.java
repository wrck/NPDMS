package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionRuleSelectionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionRuleSelectionMapper extends BaseMapperX<InspectionRuleSelectionDO> {
    default List<InspectionRuleSelectionDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionRuleSelectionDO>()
                .eq(InspectionRuleSelectionDO::getTenantId, query.tenantId())
                .eq(InspectionRuleSelectionDO::getTaskId, query.taskId()).orderByAsc(InspectionRuleSelectionDO::getId));
    }
    int clearPreparation(@Param("query") InspectionTaskRows query);
}
