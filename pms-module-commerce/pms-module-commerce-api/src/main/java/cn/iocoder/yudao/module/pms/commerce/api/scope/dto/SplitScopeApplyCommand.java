package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SplitScopeApplyCommand(Long tenantId, Long parentProjectId, Integer expectedParentProjectVersion,
                                     Long expectedScopeVersion, String idempotencyKey,
                                     Map<String, Long> projectIdsByClientItemKey,
                                     Map<String, Integer> projectVersionsByClientItemKey,
                                     List<Allocation> allocations) {

    public record Allocation(String clientItemKey, Long orderLineId, BigDecimal quantity,
                             String officeDepartmentCode, List<String> serialNumbers) {
    }
}
