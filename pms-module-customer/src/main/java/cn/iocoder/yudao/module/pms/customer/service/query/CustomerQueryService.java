package cn.iocoder.yudao.module.pms.customer.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerDetailQuery;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.VisibleCustomerPageQuery;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class CustomerQueryService {

    @Resource
    private CustomerMasterMapper customerMasterMapper;

    public PageResult<CustomerMasterDO> page(CustomerPageCriteria criteria, CustomerVisibleScope scope) {
        validate(criteria, scope);
        if (!scope.all() && scope.slices().isEmpty()) {
            return PageResult.empty();
        }
        return customerMasterMapper.selectVisiblePage(new VisibleCustomerPageQuery(
                criteria.tenantId(), criteria.code(), criteria.name(), criteria.departmentCode(),
                criteria.marketCode(), criteria.systemCode(), criteria.expendCode(), criteria.industryCode(),
                criteria.lifecycleStatus(), criteria.sourceType(), scope.all(), scope.slices(), criteria.pageParam()));
    }

    public CustomerMasterDO get(Long tenantId, Long customerId, CustomerVisibleScope scope) {
        if (tenantId == null || customerId == null || scope == null || scope.slices() == null) {
            throw new IllegalArgumentException("客户详情查询不完整");
        }
        if (!scope.all() && scope.slices().isEmpty()) {
            return null;
        }
        return customerMasterMapper.selectVisibleById(
                new VisibleCustomerDetailQuery(tenantId, customerId, scope.all(), scope.slices()));
    }

    private void validate(CustomerPageCriteria criteria, CustomerVisibleScope scope) {
        if (criteria == null || criteria.tenantId() == null || criteria.pageParam() == null || scope == null
                || scope.slices() == null) {
            throw new IllegalArgumentException("客户分页查询不完整");
        }
    }
}
