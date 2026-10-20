package cn.iocoder.yudao.module.pms.project.api.projectgovernance;

import java.time.LocalDateTime;
import java.util.Optional;

/** Owner consumers read the existing governance snapshot; no separate restart lifecycle or ledger. */
public interface ProjectReopenScopeApi {
    record Query(Long tenantId, Long projectId) { }
    record Round(Long snapshotId, LocalDateTime openedAt) { }
    Optional<Round> findTemplateReopen(Query query);
}
