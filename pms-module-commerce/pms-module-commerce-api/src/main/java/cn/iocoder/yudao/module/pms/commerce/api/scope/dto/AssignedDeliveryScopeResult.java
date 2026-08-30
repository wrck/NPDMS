package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** COM返回的项目当前已分配范围完整事实。 */
public record AssignedDeliveryScopeResult(Long projectId, Long scopeVersion,
                                          List<AssignedDeliveryScopeLine> assignedLines) {

    private static final Comparator<AssignedDeliveryScopeLine> STABLE_ORDER = Comparator
            .comparing(AssignedDeliveryScopeLine::orderLineId)
            .thenComparing(AssignedDeliveryScopeLine::scopeId)
            .thenComparing(AssignedDeliveryScopeLine::scopeDetailId);

    public AssignedDeliveryScopeResult {
        if (projectId == null || projectId <= 0) {
            throw corrupted("projectId must be positive");
        }
        if (scopeVersion == null || scopeVersion < 0) {
            throw corrupted("scopeVersion must be non-negative");
        }
        if (assignedLines == null || assignedLines.stream().anyMatch(line -> line == null)) {
            throw corrupted("assignedLines must be a complete list");
        }
        assignedLines = assignedLines.stream().sorted(STABLE_ORDER).toList();
        Set<String> groupingKeys = new HashSet<>();
        for (AssignedDeliveryScopeLine line : assignedLines) {
            String key = line.scopeId() + ":" + line.scopeDetailId();
            if (!groupingKeys.add(key)) {
                throw corrupted("duplicate scopeId and scopeDetailId grouping key");
            }
        }
    }

    private static DeliveryScopeFactException corrupted(String message) {
        return new DeliveryScopeFactException(DeliveryScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
