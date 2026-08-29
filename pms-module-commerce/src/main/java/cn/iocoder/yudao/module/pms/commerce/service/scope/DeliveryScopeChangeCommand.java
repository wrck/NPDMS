package cn.iocoder.yudao.module.pms.commerce.service.scope;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryScopeChangeCommand(
        Long tenantId,
        Long subjectUserId,
        Long deliveryScopeId,
        Long projectId,
        Integer expectedProjectVersion,
        Long expectedProjectScopeVersion,
        Long expectedAllocationVersion,
        String expectedOrderLineSourceVersion,
        BigDecimal proposedAllocatedQuantity,
        List<String> serialNumbers,
        String reason,
        String operationId) {

    public DeliveryScopeChangeCommand {
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);
    }
}
