package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

import java.math.BigDecimal;

public record AcceptanceScopeGuardQuery(
        Long tenantId,
        Long projectId,
        Long deliveryScopeId,
        Long currentAllocationVersion,
        BigDecimal proposedAllocatedQty,
        String operationId) {
}
