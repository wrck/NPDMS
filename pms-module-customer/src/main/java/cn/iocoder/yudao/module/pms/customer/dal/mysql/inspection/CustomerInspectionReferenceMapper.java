package cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.inspection.CustomerInspectionReferenceDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection.query.CustomerInspectionReferenceQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CustomerInspectionReferenceMapper extends BaseMapperX<CustomerInspectionReferenceDO> {
    default List<CustomerInspectionReferenceDO> selectForHistory(CustomerInspectionReferenceQuery query) {
        if (query.historyIds() == null || query.historyIds().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<CustomerInspectionReferenceDO>()
                .eq(CustomerInspectionReferenceDO::getTenantId, query.tenantId())
                .in(CustomerInspectionReferenceDO::getHistoryId, query.historyIds()).orderByAsc(CustomerInspectionReferenceDO::getId));
    }
}
