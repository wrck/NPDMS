package cn.iocoder.yudao.module.pms.commerce.api.migration;

import java.time.LocalDateTime;
import java.util.List;

/** INT owns source identities; COM owns the complete formal plan and contract references. */
public interface ContractCollectionPlanMigrationApi {
    record Plan(Long id, Long tenantId, Long contractId, String contractNo, String batchCode,
                String collectionItemName, String referenceEventName, LocalDateTime eventPlannedAt,
                Integer afterDays, LocalDateTime eventActualFinishedAt, String marketingFeedback,
                String dataSource, LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
                String creator, LocalDateTime createTime, String updater, LocalDateTime updateTime,
                Boolean deleted) {}
    /** adjudicatedExistingId is used only for explicit, reviewed adoption of the old ten records. */
    record Entry(String sourceKey, Long targetId, Long adjudicatedExistingId, Plan lastApplied, Plan incoming) {}
    record Request(Long tenantId, Long actorId, String owner, List<Entry> entries, boolean adoptExisting) {}
    record Result(String sourceKey, String action, Plan before, Plan after, String message) {}
    List<Result> preview(Request request);
    /** Requires the INT transaction that also persists source bindings and run evidence. */
    List<Result> apply(Request request);
}
