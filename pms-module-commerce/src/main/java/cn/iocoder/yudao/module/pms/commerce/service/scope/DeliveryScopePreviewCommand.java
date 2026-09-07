package cn.iocoder.yudao.module.pms.commerce.service.scope;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryScopePreviewCommand(
        Long tenantId, Long subjectUserId, Long projectId, Integer expectedProjectVersion,
        Long expectedProjectScopeVersion, Long expectedDeliveryScopeVersion,
        Long orderLineId, String expectedOrderLineSourceVersion,
        BigDecimal proposedQuantity, List<String> serialNumbers,
        Long deliveryScopeId, Long expectedAllocationVersion) {

    public DeliveryScopePreviewCommand {
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);
    }
}
