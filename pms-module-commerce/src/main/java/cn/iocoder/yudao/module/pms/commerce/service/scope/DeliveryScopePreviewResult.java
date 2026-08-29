package cn.iocoder.yudao.module.pms.commerce.service.scope;

import java.math.BigDecimal;
import java.util.List;

public record DeliveryScopePreviewResult(
        Long projectId, Integer projectVersion, String projectCode,
        Long officeDepartmentId, String officeDepartmentCode, String officeDepartmentName,
        Integer officeDepartmentVersion, Long orderLineId, String orderLineSourceVersion,
        BigDecimal orderQuantity, BigDecimal allocatedQuantity, BigDecimal availableQuantity,
        BigDecimal proposedQuantity, boolean allowed, List<String> validationErrors,
        List<OccupiedScope> occupiedScopes) {

    public record OccupiedScope(Long deliveryScopeId, Long projectId, BigDecimal allocatedQuantity,
                                Long allocationVersion, String scopeStatus) {
    }
}
