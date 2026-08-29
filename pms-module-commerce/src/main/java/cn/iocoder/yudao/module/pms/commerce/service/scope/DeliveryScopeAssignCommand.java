package cn.iocoder.yudao.module.pms.commerce.service.scope;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryScopeAssignCommand(
        Long tenantId,
        Long subjectUserId,
        Long projectId,
        Integer expectedProjectVersion,
        Long expectedProjectScopeVersion,
        Long orderLineId,
        String expectedOrderLineSourceVersion,
        BigDecimal allocatedQuantity,
        List<String> serialNumbers,
        String reason,
        String operationId) {

    public DeliveryScopeAssignCommand {
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);
    }
}
