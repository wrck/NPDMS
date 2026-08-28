package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import java.math.BigDecimal;
import java.util.List;

public record SplitScopePreviewCommand(Long tenantId, Long parentProjectId, Long expectedScopeVersion,
                                       List<Allocation> allocations) {

    public record Allocation(String clientItemKey, Long orderLineId, BigDecimal quantity,
                             String officeDepartmentCode, List<String> serialNumbers) {
    }
}
