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
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.customer.enums.ErrorCodeConstants.CUSTOMER_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class CustomerQueryApiImpl implements CustomerQueryApi {
    @Override
    public java.util.Set<Long> findCustomerIdsByName(NameQuery query) {
        if (!java.util.Objects.equals(TenantContextHolder.getRequiredTenantId(), query.tenantId())
                || query.keyword() == null || query.keyword().isBlank()) return java.util.Set.of();
        return customerMasterMapper.selectIdsByName(
                new cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query.CustomerNameQuery(
                        query.tenantId(), query.keyword().trim()));
    }

    private final CustomerMasterMapper customerMasterMapper;
    private final CustomerQueryService customerQueryService;
    private final CustomerScopeContextService scopeContextService;
    private final PermissionApi permissionApi;
    @jakarta.annotation.Resource private ProjectBusinessContextApi projectContexts;
    @jakarta.annotation.Resource private ProjectScopeApi projectScopes;

    @Override
    public ProjectCustomerContext getProjectCustomer(ProjectQuery query) {
        var actor = SecurityFrameworkUtils.getLoginUser();
        if (query == null || actor == null || TenantContextHolder.isIgnore()
                || query.tenantId() == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(query.tenantId(), actor.getTenantId())
                || query.actorUserId() == null || !Objects.equals(query.actorUserId(), actor.getId())
                || query.projectId() == null || query.projectId() <= 0) throw exception(FORBIDDEN);
        if (!permissionApi.hasAnyPermissions(query.actorUserId(), "pms:customer:query")) throw exception(FORBIDDEN);
        var scope = projectScopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(query.tenantId()).subjectUserId(query.actorUserId()).anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (scope == null || scope.treeVersion() == null || !scope.fullProjectIds().contains(query.projectId()))
            throw exception(FORBIDDEN);
        var project = projectContexts.inspect(new ProjectBusinessContextApi.Query(
                query.tenantId(), query.actorUserId(), query.projectId(), scope.treeVersion()));
        if (project == null || !Objects.equals(query.tenantId(), project.tenantId())
                || !Objects.equals(query.projectId(), project.projectId())
                || !Objects.equals(scope.treeVersion(), project.scopeVersion())) throw exception(FORBIDDEN);
        CustomerMasterDO customer;
        if (project.customerId() != null) customer = customerMasterMapper.selectById(project.customerId());
        else if (project.customerCode() != null && !project.customerCode().isBlank())
            customer = customerMasterMapper.selectByTenantIdAndCode(query.tenantId(), project.customerCode().trim());
        else return new ProjectCustomerContext(project.projectId(), project.projectVersion(), project.lifecycleStatus(), null);
        if (customer == null || !Objects.equals(query.tenantId(), customer.getTenantId())
                || CustomerLifecycleStatus.DELETED.name().equals(customer.getLifecycleStatus())) throw exception(CUSTOMER_NOT_EXISTS);
        return new ProjectCustomerContext(project.projectId(), project.projectVersion(), project.lifecycleStatus(), toSummary(customer));
    }

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
