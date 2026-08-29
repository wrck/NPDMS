package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractDetailScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractRelationListQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ContractRelatedOrderQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ContractAccessService {

    private final OrganizationScopeApi organizationScopeApi;
    private final ProjectScopeApi projectScopeApi;
    private final ContractMapper contractMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderLineMapper lineMapper;
    private final ProjectContractRelationMapper projectRelationMapper;
    private final OperationAuditApi operationAuditApi;

    public PageResult<ContractDO> pageContracts(Long tenantId, Long subjectUserId, String correlationId,
                                                 ContractSearch criteria) {
        AccessScope scope = currentAccessScope(tenantId, subjectUserId, correlationId);
        if (scope.empty()) return PageResult.empty();
        ContractCompanyScopeQuery query = new ContractCompanyScopeQuery(tenantId, scope.companyCodes(),
                scope.projectIds(),
                criteria.companyCode(), criteria.contractNo(), criteria.contractType(), criteria.customerKeyword(),
                criteria.sourceSystem(), criteria.status(), checkedOffset(criteria.offset()),
                checkedLimit(criteria.limit()));
        long total = contractMapper.selectCountByCompanyScope(query);
        return total == 0L ? PageResult.empty() : new PageResult<>(contractMapper.selectByCompanyScope(query), total);
    }

    public ContractDetail getContractDetail(Long tenantId, Long subjectUserId, String correlationId,
                                             Long contractId) {
        AccessScope scope = currentAccessScope(tenantId, subjectUserId, correlationId);
        if (scope.empty()) throw inaccessible();
        ContractDO contract = contractMapper.selectDetailByCompanyScope(
                new ContractDetailScopeQuery(tenantId, contractId, scope.companyCodes(), scope.projectIds()));
        if (contract == null) throw inaccessible();
        List<SalesOrderDO> orders = orderMapper.selectRelatedByContract(
                new ContractRelatedOrderQuery(tenantId, contractId));
        List<ProjectContractRelationDO> relations = projectRelationMapper.selectCurrentByContract(
                new ContractRelationListQuery(tenantId, contractId));
        return new ContractDetail(contract, orders == null ? List.of() : List.copyOf(orders),
                relations == null ? List.of() : List.copyOf(relations));
    }

    public PageResult<SalesOrderDO> pageSalesOrders(Long tenantId, Long subjectUserId, String correlationId,
                                                     SalesOrderSearch criteria) {
        AccessScope scope = currentAccessScope(tenantId, subjectUserId, correlationId);
        if (scope.empty()) return PageResult.empty();
        SalesOrderCompanyScopeQuery query = new SalesOrderCompanyScopeQuery(tenantId, scope.companyCodes(),
                scope.projectIds(), criteria.companyCode(), criteria.orderNo(), criteria.orderType(),
                criteria.customerKeyword(), criteria.status(), checkedOffset(criteria.offset()),
                checkedLimit(criteria.limit()));
        long total = orderMapper.selectCountByCompanyScope(query);
        return total == 0L ? PageResult.empty() : new PageResult<>(orderMapper.selectByCompanyScope(query), total);
    }

    public PageResult<SalesOrderLineDO> pageSalesOrderLines(Long tenantId, Long subjectUserId, String correlationId,
                                                             SalesOrderLineSearch criteria) {
        AccessScope scope = currentAccessScope(tenantId, subjectUserId, correlationId);
        if (scope.empty()) return PageResult.empty();
        SalesOrderLineCompanyScopeQuery query = new SalesOrderLineCompanyScopeQuery(tenantId,
                scope.companyCodes(), scope.projectIds(), criteria.orderId(), criteria.companyCode(),
                criteria.orderType(), criteria.orderNo(), criteria.lineNo(), criteria.itemCode(),
                criteria.productCode(), criteria.quantityStatus(), criteria.status(),
                checkedOffset(criteria.offset()), checkedLimit(criteria.limit()));
        long total = lineMapper.selectCountByCompanyScope(query);
        return total == 0L ? PageResult.empty() : new PageResult<>(lineMapper.selectByCompanyScope(query), total);
    }

    private AccessScope currentAccessScope(Long tenantId, Long subjectUserId, String correlationId) {
        return new AccessScope(currentCompanyCodes(tenantId, subjectUserId, correlationId),
                currentProjectIds(tenantId, subjectUserId));
    }

    private List<UserCompanyDepartmentScopeRespDTO> currentScopes(
            Long tenantId, Long subjectUserId, String correlationId) {
        try {
            List<UserCompanyDepartmentScopeRespDTO> scopes = organizationScopeApi.getActiveScopes(subjectUserId);
            return scopes == null ? List.of() : scopes;
        } catch (RuntimeException exception) {
            try {
                operationAuditApi.record(tenantId, subjectUserId, correlationId,
                        "COM_CONTRACT_AUTHORIZATION", "ContractDirectory", String.valueOf(subjectUserId),
                        "OWNER_UNAVAILABLE", java.util.Map.of("owner", "SYSTEM.OrganizationScopeApi"));
            } catch (RuntimeException ignored) {
                // 授权Owner不可用时该路径失败关闭；审计失败不得扩大结果集。
            }
            return List.of();
        }
    }

    List<String> currentCompanyCodes(Long tenantId, Long subjectUserId, String correlationId) {
        TreeSet<String> codes = new TreeSet<>();
        for (UserCompanyDepartmentScopeRespDTO scope : currentScopes(tenantId, subjectUserId, correlationId)) {
            if (scope != null && scope.getId() != null && scope.getVersion() != null
                    && scope.getCompanyCode() != null && !scope.getCompanyCode().isBlank()) {
                codes.add(scope.getCompanyCode());
            }
        }
        return List.copyOf(codes);
    }

    private List<Long> currentProjectIds(Long tenantId, Long subjectUserId) {
        try {
            Set<Long> projectIds = projectScopeApi.resolveAllCurrent(
                    new ProjectAllScopeQuery(tenantId, subjectUserId, ProjectScopeApi.ACTION_VIEW));
            return projectIds == null ? List.of()
                    : projectIds.stream().filter(java.util.Objects::nonNull).sorted().toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private int checkedOffset(int offset) {
        if (offset < 0) throw new IllegalArgumentException("COMMERCE_QUERY_OFFSET_INVALID");
        return offset;
    }

    private int checkedLimit(int limit) {
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("COMMERCE_QUERY_LIMIT_INVALID");
        return limit;
    }

    static IllegalStateException inaccessible() {
        return new IllegalStateException("COMMERCE_RESOURCE_NOT_ACCESSIBLE");
    }

    public record ContractSearch(String companyCode, String contractNo, String contractType,
                                 String customerKeyword, String sourceSystem, String status,
                                 int offset, int limit) {
    }

    public record SalesOrderSearch(String companyCode, String orderNo, String orderType,
                                   String customerKeyword, String status, int offset, int limit) {
    }

    public record SalesOrderLineSearch(Long orderId, String companyCode, String orderType, String orderNo,
                                       String lineNo, String itemCode, String productCode,
                                       String quantityStatus, String status, int offset, int limit) {
    }

    public record ContractDetail(ContractDO contract, List<SalesOrderDO> relatedOrders,
                                 List<ProjectContractRelationDO> projectRelations) {
    }

    private record AccessScope(List<String> companyCodes, List<Long> projectIds) {
        boolean empty() {
            return companyCodes.isEmpty() && projectIds.isEmpty();
        }
    }
}
