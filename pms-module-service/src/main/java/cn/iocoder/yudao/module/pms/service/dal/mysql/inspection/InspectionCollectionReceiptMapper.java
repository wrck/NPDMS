package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionCollectionReceiptDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionExecutionRows;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionCollectionReceiptMapper extends BaseMapperX<InspectionCollectionReceiptDO> {
    List<InspectionCollectionReceiptDO> selectForExecutionForUpdate(@Param("query") InspectionExecutionRows query);
    default List<InspectionCollectionReceiptDO> selectForExecution(InspectionExecutionRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionCollectionReceiptDO>()
                .eq(InspectionCollectionReceiptDO::getTenantId, query.tenantId())
                .eq(InspectionCollectionReceiptDO::getExecutionId, query.executionId()).orderByAsc(InspectionCollectionReceiptDO::getResultVersion));
    }
    default List<InspectionCollectionReceiptDO> selectForTask(InspectionTaskRows query) {
        return selectList(new LambdaQueryWrapperX<InspectionCollectionReceiptDO>()
                .eq(InspectionCollectionReceiptDO::getTenantId, query.tenantId())
                .eq(InspectionCollectionReceiptDO::getTaskId, query.taskId()).orderByAsc(InspectionCollectionReceiptDO::getId));
    }
}
