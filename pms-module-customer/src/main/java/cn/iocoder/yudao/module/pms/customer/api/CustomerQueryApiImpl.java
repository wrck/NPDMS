package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerLifecycleStatus;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerQueryApiImpl implements CustomerQueryApi {

    private final CustomerMasterMapper customerMasterMapper;
    private final CustomerQueryService customerQueryService;
    private final CustomerScopeContextService scopeContextService;
    private final PermissionApi permissionApi;

    @Override
    public CustomerSummaryDTO getCustomerByCode(CustomerCodeQuery query) {
        if (query == null || query.actorUserId() == null || query.code() == null || query.code().isBlank()) {
            throw new IllegalArgumentException("客户编码查询不完整");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (!permissionApi.hasAnyPermissions(query.actorUserId(), "pms:customer:query")) return null;
        return toSummary(customerQueryService.getByCode(tenantId, query.code(),
                scopeContextService.resolve(tenantId, query.actorUserId())));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public CustomerSummaryDTO lockCustomerByCode(CustomerCodeQuery query) {
        var visible = getCustomerByCode(query);
        if (visible == null) return null;
        var locked = customerMasterMapper.selectIncludingDeletedForUpdate(TenantContextHolder.getRequiredTenantId(), visible.id());
        if (locked == null || locked.getVersion() == null
                || !java.util.Objects.equals(locked.getVersion().longValue(), visible.version())) return null;
        return toSummary(locked);
    }

    @Override
    public CustomerSummaryDTO getCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return toSummary(customerMasterMapper.selectById(customerId));
    }

    @Override
    public List<CustomerSummaryDTO> getCustomers(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return customerMasterMapper.selectByIds(customerIds).stream()
                .map(this::toSummary)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private CustomerSummaryDTO toSummary(CustomerMasterDO customer) {
        if (customer == null || CustomerLifecycleStatus.DELETED.name().equals(customer.getLifecycleStatus())) {
            return null;
        }
        return new CustomerSummaryDTO(customer.getId(), customer.getTenantId(), customer.getCode(), customer.getName(),
                customer.getShortName(), customer.getLifecycleStatus(), customer.getSourceType(),
                customer.getVersion() == null ? null : customer.getVersion().longValue(), customer.getDataAsOf());
    }
}
