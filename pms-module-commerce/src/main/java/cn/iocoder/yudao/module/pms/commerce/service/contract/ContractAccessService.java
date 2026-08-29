package cn.iocoder.yudao.module.pms.commerce.service.contract;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractDetailScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ContractAccessService {

    private final OrganizationScopeApi organizationScopeApi;
    private final ContractMapper contractMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderLineMapper lineMapper;
    private final OperationAuditApi operationAuditApi;

    public List<ContractDO> listContracts(Long tenantId, Long subjectUserId, String correlationId, String contractNo,
                                          String status, int offset, int limit) {
        List<String> companyCodes = currentCompanyCodes(tenantId, subjectUserId, correlationId);
        if (companyCodes.isEmpty()) {
            return List.of();
        }
        return contractMapper.selectByCompanyScope(new ContractCompanyScopeQuery(
                tenantId, companyCodes, contractNo, status, checkedOffset(offset), checkedLimit(limit)));
    }

    public ContractDO getContract(Long tenantId, Long subjectUserId, String correlationId, Long contractId) {
        List<String> companyCodes = currentCompanyCodes(tenantId, subjectUserId, correlationId);
        if (companyCodes.isEmpty()) {
            throw inaccessible();
        }
        ContractDO contract = contractMapper.selectDetailByCompanyScope(
                new ContractDetailScopeQuery(tenantId, contractId, companyCodes));
        if (contract == null) {
            throw inaccessible();
        }
        return contract;
    }

    public List<SalesOrderDO> listSalesOrders(Long tenantId, Long subjectUserId, String correlationId, String orderNo,
                                               String status, int offset, int limit) {
        List<String> companyCodes = currentCompanyCodes(tenantId, subjectUserId, correlationId);
        if (companyCodes.isEmpty()) {
            return List.of();
        }
        return orderMapper.selectByCompanyScope(new SalesOrderCompanyScopeQuery(
                tenantId, companyCodes, orderNo, status, checkedOffset(offset), checkedLimit(limit)));
    }

    public List<SalesOrderLineDO> listSalesOrderLines(Long tenantId, Long subjectUserId, String correlationId,
                                                       Long orderId,
                                                       String lineNo, int offset, int limit) {
        List<String> companyCodes = currentCompanyCodes(tenantId, subjectUserId, correlationId);
        if (companyCodes.isEmpty()) {
            return List.of();
        }
        return lineMapper.selectByCompanyScope(new SalesOrderLineCompanyScopeQuery(
                tenantId, companyCodes, orderId, lineNo, checkedOffset(offset), checkedLimit(limit)));
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
                // 授权Owner不可用时查询仍须失败关闭为空；审计失败不得扩大结果集。
            }
            return List.of();
        }
    }

    List<String> currentCompanyCodes(Long tenantId, Long subjectUserId, String correlationId) {
        TreeSet<String> codes = new TreeSet<>();
        for (UserCompanyDepartmentScopeRespDTO scope : currentScopes(
                tenantId, subjectUserId, correlationId)) {
            if (scope != null && scope.getId() != null && scope.getVersion() != null
                    && scope.getCompanyCode() != null && !scope.getCompanyCode().isBlank()) {
                codes.add(scope.getCompanyCode());
            }
        }
        return List.copyOf(codes);
    }

    private int checkedOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("COMMERCE_QUERY_OFFSET_INVALID");
        }
        return offset;
    }

    private int checkedLimit(int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("COMMERCE_QUERY_LIMIT_INVALID");
        }
        return limit;
    }

    static IllegalStateException inaccessible() {
        return new IllegalStateException("COMMERCE_RESOURCE_NOT_ACCESSIBLE");
    }
}
