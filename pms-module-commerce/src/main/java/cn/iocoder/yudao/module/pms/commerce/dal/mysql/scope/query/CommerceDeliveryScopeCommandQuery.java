package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.time.LocalDateTime;
import java.util.List;

/** Task 5场景化锁查询与CAS参数。 */
public final class CommerceDeliveryScopeCommandQuery {

    private CommerceDeliveryScopeCommandQuery() {
    }

    public record ProjectVersionSeed(Long id, Long tenantId, Long projectId, String actor,
                                     LocalDateTime now) {
    }

    public record ProjectLock(Long tenantId, Long projectId) {
    }

    public record OrderLinesLock(Long tenantId, List<Long> orderLineIds) {
    }

    public record CurrentScopesLock(Long tenantId, List<Long> orderLineIds) {
    }

    public record ScopeDetailsLock(Long tenantId, List<Long> scopeIds) {
    }

    public record AllocationVersionQuery(Long tenantId, Long projectId, List<Long> orderLineIds) {
    }

    public record AllocationVersionFact(Long orderLineId, Long maxAllocationVersion) {
    }

    public record EndScope(Long tenantId, Long scopeId, Integer expectedVersion, LocalDateTime effectiveTo,
                           String actor, LocalDateTime now) {
    }

    public record EndDetails(Long tenantId, Long scopeId, String actor, LocalDateTime now) {
    }

    public record AdvanceProjectVersion(Long tenantId, Long projectId, Long expectedScopeVersion,
                                        Integer expectedRowVersion, Long newScopeVersion,
                                        Integer newPayloadVersion, String changeType,
                                        String actor, LocalDateTime now) {
    }
}
