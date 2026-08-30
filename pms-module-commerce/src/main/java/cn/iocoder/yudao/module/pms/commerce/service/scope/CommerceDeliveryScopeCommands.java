package cn.iocoder.yudao.module.pms.commerce.service.scope;

import java.math.BigDecimal;
import java.util.List;

/** F-COM-001交付范围写命令；Controller只负责把受信上下文转换为这些内部命令。 */
public final class CommerceDeliveryScopeCommands {

    private CommerceDeliveryScopeCommands() {
    }

    public record ApplyCommand(Long tenantId, Long projectId, Long actorId, Long expectedScopeVersion,
                               List<ScopeLine> lines, String reason, String idempotencyKey,
                               String correlationId) {
    }

    public record ReleaseCommand(Long tenantId, Long projectId, Long actorId, Long expectedScopeVersion,
                                 List<Long> orderLineIds, String reason, String releaseEvidence,
                                 String idempotencyKey, String correlationId) {
    }

    public record ResolveConflictCommand(Long tenantId, Long projectId, Long actorId, Long expectedScopeVersion,
                                         Resolution resolution, List<ScopeLine> lines, List<Long> orderLineIds,
                                         String evidence, String idempotencyKey, String correlationId) {
    }

    public record ScopeLine(Long orderLineId, String expectedSourceVersion, BigDecimal quantity,
                            String unitCode, List<ScopeDetail> details) {
    }

    public record ScopeDetail(String officeDepartmentCode, BigDecimal quantity, String unitCode,
                              String productCode, String modelCode, String serialNumber,
                              Location location) {
    }

    public record Location(LocationResolution resolution, Long siteId, Integer siteVersion,
                           Long siteLocationId, Integer siteLocationVersion, String locationText) {
    }

    public enum LocationResolution { RESOLVED, UNRESOLVED }

    public enum Resolution { ACTIVE, RELEASED }

    public record CommandResult(String action, Long projectId, Long scopeVersion,
                                List<Long> affectedScopeIds, boolean protectedAsConflict) {

        public CommandResult {
            affectedScopeIds = affectedScopeIds == null ? List.of() : List.copyOf(affectedScopeIds);
        }
    }
}
