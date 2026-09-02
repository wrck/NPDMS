package cn.iocoder.yudao.module.pms.cutover.service.dashboard.port;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** CUT consumer port for resolving dashboard action facts in one batch. */
public interface CutoverDashboardActionFactPort {

    List<CutoverDashboardActionFacts> inspectBatch(BatchQuery query);

    record BatchQuery(Long tenantId, Long actorId, List<CandidateNeed> candidates) {
        public BatchQuery {
            if (tenantId == null || tenantId <= 0 || actorId == null || actorId <= 0
                    || candidates == null || candidates.isEmpty()) {
                throw new IllegalArgumentException("dashboard batch query is incomplete");
            }
            candidates = List.copyOf(candidates);
            Set<Long> taskIds = new HashSet<>();
            long previous = 0L;
            for (CandidateNeed candidate : candidates) {
                if (candidate == null || candidate.taskId() == null || candidate.taskId() <= 0
                        || candidate.projectId() == null || candidate.projectId() <= 0
                        || candidate.taskVersion() == null || candidate.taskVersion() < 0
                        || candidate.currentStage() == null || candidate.currentStage().isBlank()
                        || !taskIds.add(candidate.taskId()) || candidate.taskId() <= previous) {
                    throw new IllegalArgumentException("dashboard candidates must be unique and sorted by taskId");
                }
                previous = candidate.taskId();
            }
        }
    }

    record CandidateNeed(Long taskId, Long projectId, Integer taskVersion, String currentStage,
                         Long stageFactId, Integer stageFactVersion) {
        public CandidateNeed {
            currentStage = currentStage == null ? null : currentStage.trim();
            if (stageFactId != null && stageFactId <= 0
                    || stageFactVersion != null && stageFactVersion < 0) {
                throw new IllegalArgumentException("dashboard stage fact identity is invalid");
            }
        }
    }
}
