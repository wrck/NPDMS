package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.AssignedDeliveryScopeLine;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.AssignedDeliveryScopeResult;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.SalesOrderLineMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineIdsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeDetailMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeProjectVersionMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeDetailIdsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectVersionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignedDeliveryScopeQueryService {
    private final DeliveryScopeProjectVersionMapper versionMapper;
    private final DeliveryScopeMapper scopeMapper;
    private final DeliveryScopeDetailMapper detailMapper;
    private final SalesOrderLineMapper orderLineMapper;

    @Transactional(rollbackFor = Exception.class)
    public AssignedDeliveryScopeResult getAssignedScope(Long projectId, Long expectedScopeVersion) {
        Long tenantId = trustedTenantId(projectId);
        DeliveryScopeProjectQuery scopeQuery = new DeliveryScopeProjectQuery(tenantId, projectId);
        DeliveryScopeProjectVersionQuery versionQuery = new DeliveryScopeProjectVersionQuery(tenantId, projectId);
        List<DeliveryScopeDO> scopes = expectedScopeVersion == null
                ? scopeMapper.selectCurrentByProjectId(scopeQuery)
                : scopeMapper.selectCurrentByProjectIdForUpdate(scopeQuery);
        DeliveryScopeProjectVersionDO version = expectedScopeVersion == null
                ? versionMapper.selectCurrent(versionQuery) : versionMapper.selectForUpdate(versionQuery);
        long currentVersion = version == null ? 0L : version.getScopeVersion();
        if (expectedScopeVersion != null && !Objects.equals(expectedScopeVersion, currentVersion)) {
            throw new DeliveryScopeFactException(DeliveryScopeFactException.Code.SCOPE_STALE,
                    "project scope version changed");
        }
        if (scopes.stream().anyMatch(scope -> scope.getScopeStatus() != null
                && scope.getScopeStatus().startsWith("CONFLICT"))) {
            throw new DeliveryScopeFactException(DeliveryScopeFactException.Code.SCOPE_CONFLICT,
                    "project has unresolved delivery-scope conflict");
        }
        List<DeliveryScopeDO> active = scopes.stream()
                .filter(scope -> "ACTIVE".equals(scope.getScopeStatus())).toList();
        if (active.isEmpty()) {
            return new AssignedDeliveryScopeResult(projectId, currentVersion, List.of());
        }
        List<Long> scopeIds = active.stream().map(DeliveryScopeDO::getId).toList();
        List<DeliveryScopeDetailDO> details = detailMapper.selectByScopeIds(
                new DeliveryScopeDetailIdsQuery(tenantId, scopeIds));
        Map<Long, SalesOrderLineDO> orderLines = orderLineMapper.selectByIds(new SalesOrderLineIdsQuery(
                        tenantId, active.stream().map(DeliveryScopeDO::getOrderLineId).distinct().toList()))
                .stream().collect(Collectors.toMap(SalesOrderLineDO::getId, Function.identity()));
        Map<Long, DeliveryScopeDO> scopesById = active.stream()
                .collect(Collectors.toMap(DeliveryScopeDO::getId, Function.identity()));
        List<AssignedDeliveryScopeLine> assigned = details.stream()
                .filter(detail -> "ACTIVE".equals(detail.getDetailStatus()))
                .map(detail -> toAssigned(detail, scopesById, orderLines))
                .toList();
        if (assigned.isEmpty() || assigned.size() != details.stream()
                .filter(detail -> "ACTIVE".equals(detail.getDetailStatus())).count()) {
            throw corrupted("active delivery scope has incomplete qualified detail");
        }
        return new AssignedDeliveryScopeResult(projectId, currentVersion, assigned);
    }

    private AssignedDeliveryScopeLine toAssigned(DeliveryScopeDetailDO detail,
                                                  Map<Long, DeliveryScopeDO> scopes,
                                                  Map<Long, SalesOrderLineDO> orderLines) {
        DeliveryScopeDO scope = scopes.get(detail.getDeliveryScopeId());
        SalesOrderLineDO line = scope == null ? null : orderLines.get(scope.getOrderLineId());
        if (scope == null || line == null || !"CONFIRMED".equals(line.getQuantityStatus())
                || line.getSourceLifecycleStatus() != null
                && !"ACTIVE".equals(line.getSourceLifecycleStatus())) {
            throw corrupted("delivery scope references an unavailable order-line owner");
        }
        String productCode = nonblank(detail.getProductCode()) ? detail.getProductCode() : line.getProductCode();
        String modelCode = nonblank(detail.getDeviceTypeCode()) ? detail.getDeviceTypeCode() : line.getModelCode();
        List<String> serials = nonblank(detail.getSerialNo()) ? List.of(detail.getSerialNo()) : List.of();
        return new AssignedDeliveryScopeLine(scope.getId(), detail.getId(), scope.getOrderLineId(),
                detail.getAllocatedQty(), line.getUnitCode(), productCode, modelCode, serials);
    }

    private Long trustedTenantId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            throw new DeliveryScopeFactException(DeliveryScopeFactException.Code.INVALID_REQUEST,
                    "projectId must be positive");
        }
        try {
            return TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException exception) {
            throw new DeliveryScopeFactException(DeliveryScopeFactException.Code.TENANT_CONTEXT_MISMATCH,
                    "trusted tenant context is required", exception);
        }
    }

    private boolean nonblank(String value) {
        return value != null && !value.isBlank();
    }

    private DeliveryScopeFactException corrupted(String message) {
        return new DeliveryScopeFactException(DeliveryScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
