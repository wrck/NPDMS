package cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.inspection.CustomerInspectionHistoryDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.inspection.query.CustomerInspectionHistoryPageQuery;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerInspectionHistoryMapper extends BaseMapperX<CustomerInspectionHistoryDO> {
    default CustomerInspectionHistoryDO selectByTenantAndTask(Long tenantId, Long taskId) {
        return selectOne(new LambdaQueryWrapperX<CustomerInspectionHistoryDO>()
                .eq(CustomerInspectionHistoryDO::getTenantId, tenantId).eq(CustomerInspectionHistoryDO::getTaskId, taskId));
    }
    default PageResult<CustomerInspectionHistoryDO> selectVisiblePage(CustomerInspectionHistoryPageQuery query) {
        if (query.visibleProjectIds() == null || query.visibleProjectIds().isEmpty()) return PageResult.empty();
        return selectPage(query.page(), new LambdaQueryWrapperX<CustomerInspectionHistoryDO>()
                .eq(CustomerInspectionHistoryDO::getTenantId, query.tenantId()).eq(CustomerInspectionHistoryDO::getCustomerId, query.customerId())
                .in(CustomerInspectionHistoryDO::getProjectId, query.visibleProjectIds()).orderByDesc(CustomerInspectionHistoryDO::getId));
    }
    default long countReferences(CustomerReferenceGuardQuery query) {
        return selectCount(new LambdaQueryWrapperX<CustomerInspectionHistoryDO>()
                .eq(CustomerInspectionHistoryDO::getTenantId, query.tenantId()).eq(CustomerInspectionHistoryDO::getCustomerId, query.customerId()));
    }
}
