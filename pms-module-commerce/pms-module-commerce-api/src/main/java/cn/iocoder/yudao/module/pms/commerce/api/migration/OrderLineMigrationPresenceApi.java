package cn.iocoder.yudao.module.pms.commerce.api.migration;
import java.util.List;
import java.util.Set;
public interface OrderLineMigrationPresenceApi {
    record Query(Long tenantId, Long actorId, List<Long> targetIds) {}
    /** Includes logically deleted rows, so their migration binding cannot be mistaken for a missing target. */
    Set<Long> existingTargets(Query query);
}
