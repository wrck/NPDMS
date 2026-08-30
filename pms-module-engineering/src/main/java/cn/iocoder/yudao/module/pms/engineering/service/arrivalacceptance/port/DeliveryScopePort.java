package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** IMP消费的COM项目已分配应到范围；生产适配等待COM正式契约。 */
public interface DeliveryScopePort {

    AssignedScope inspectAssignedScope(Long projectId);

    /** 期望版本不一致时抛出OwnerFactVersionMismatchException；不可用等故障不得伪装为版本不一致。 */
    AssignedScope lockAndRevalidate(Long projectId, Long expectedScopeVersion);

    record AssignedScope(Long projectId, Long scopeVersion, List<AssignedLine> lines) {

        public AssignedScope {
            if (projectId == null || projectId <= 0 || scopeVersion == null || scopeVersion <= 0
                    || lines == null || lines.isEmpty() || lines.stream().anyMatch(line -> line == null)) {
                throw new IllegalArgumentException("invalid assigned delivery scope");
            }
            lines = List.copyOf(lines);
        }
    }

    record AssignedLine(Long orderLineId, BigDecimal assignedQuantity, String unitCode,
                        String productCode, String modelCode, Set<String> serialNumbers) {

        public AssignedLine {
            unitCode = trimToNull(unitCode);
            productCode = trimToNull(productCode);
            modelCode = trimToNull(modelCode);
            if (orderLineId == null || orderLineId <= 0 || assignedQuantity == null
                    || assignedQuantity.signum() <= 0 || unitCode == null
                    || productCode == null && modelCode == null || serialNumbers == null) {
                throw new IllegalArgumentException("invalid assigned delivery line");
            }
            TreeSet<String> normalizedSerials = new TreeSet<>();
            for (String serialNumber : serialNumbers) {
                String normalized = trimToNull(serialNumber);
                if (normalized == null || !normalizedSerials.add(normalized)) {
                    throw new IllegalArgumentException("assigned serial number is blank or duplicated");
                }
            }
            serialNumbers = Collections.unmodifiableSet(normalizedSerials);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
