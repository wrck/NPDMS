package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateRow;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.query.CutoverDashboardCandidateQuery;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP2P3ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP4ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP5ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP6ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort.BatchQuery;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort.CandidateNeed;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only CUT dashboard aggregation. Production assembly waits for every Owner provider. */
public class CutoverDashboardQueryService {
    private static final String ACTION_VIEW = "ACTION_VIEW";
    private static final int BATCH_SIZE = 500;
    private static final Set<String> TODO_ACTIONS = Set.of(
            "SAVE_ASSESSMENT", "SUBMIT_ASSESSMENT", "GENERATE_CHECKLIST", "SAVE_CHECKLIST",
            "REQUEST_COLLECTION", "SUBMIT_CHECKLIST", "CREATE_DRAFT", "SAVE_DRAFT", "SUBMIT_PLAN",
            "REVISE_PLAN", "APPROVE", "REJECT", "CREATE_CLOSURE", "SAVE_CLOSURE",
            "LINK_MANUAL_RESULT", "SUBMIT_CLOSURE");

    private final CutoverDashboardCandidateMapper candidateMapper;
    private final CutoverProjectScopePort projectScopePort;
    private final CutoverDashboardActionFactPort actionFactPort;
    private final Clock clock;
    private final CutoverP2P3ActionPolicy p2p3Policy = new CutoverP2P3ActionPolicy();
    private final CutoverP4ActionPolicy p4Policy = new CutoverP4ActionPolicy();
    private final CutoverP5ActionPolicy p5Policy = new CutoverP5ActionPolicy();
    private final CutoverP6ActionPolicy p6Policy = new CutoverP6ActionPolicy();

    public CutoverDashboardQueryService(CutoverDashboardCandidateMapper candidateMapper,
                                        CutoverProjectScopePort projectScopePort,
                                        CutoverDashboardActionFactPort actionFactPort, Clock clock) {
        this.candidateMapper = candidateMapper;
        this.projectScopePort = projectScopePort;
        this.actionFactPort = actionFactPort;
        this.clock = clock;
    }

    public CutoverDashboardKpiView inspect(long tenantId, long actorId, PermissionFacts permissions) {
        if (tenantId <= 0 || actorId <= 0 || permissions == null) {
            throw corrupted("dashboard request identity is invalid");
        }
        Set<Long> visibleProjectIds = projectScopePort.resolveAllCurrent(actorId, ACTION_VIEW);
        if (visibleProjectIds == null) {
            throw ownerCorrupted("PROJ", "project scope result is missing");
        }
        if (visibleProjectIds.isEmpty()) {
            return new CutoverDashboardKpiView(0, 0, 0, 0, LocalDateTime.now(clock));
        }
        List<CutoverDashboardCandidateRow> rows = loadAll(tenantId, visibleProjectIds);
        long archived = rows.stream().filter(row -> "ARCHIVED".equals(row.getTaskStatus())).count();
        long approving = rows.stream().filter(row -> "P5".equals(row.getCurrentStage())
                && "APPROVING".equals(row.getTaskStatus())).count();
        long rejected = rows.stream().filter(row -> "P4".equals(row.getCurrentStage())
                && "PLAN_DRAFTING".equals(row.getTaskStatus())
                && "REJECTED".equals(row.getCurrentApprovalStatus())).count();

        List<CutoverDashboardCandidateRow> todoCandidates = rows.stream()
                .filter(CutoverDashboardQueryService::isTodoCandidate).toList();
        long todo = todoCandidates.isEmpty() ? 0
                : countTodos(tenantId, actorId, permissions, todoCandidates);
        return new CutoverDashboardKpiView(todo, archived, approving, rejected, LocalDateTime.now(clock));
    }

    private List<CutoverDashboardCandidateRow> loadAll(long tenantId, Set<Long> visibleProjectIds) {
        ArrayList<CutoverDashboardCandidateRow> all = new ArrayList<>();
        HashSet<Long> taskIds = new HashSet<>();
        long cursor = 0;
        while (true) {
            List<CutoverDashboardCandidateRow> batch = candidateMapper.selectBatch(
                    new CutoverDashboardCandidateQuery(tenantId, visibleProjectIds, cursor, BATCH_SIZE));
            if (batch == null) throw corrupted("dashboard candidate batch is missing");
            if (batch.isEmpty()) return List.copyOf(all);
            for (CutoverDashboardCandidateRow row : batch) {
                if (row == null || row.getTaskId() == null || row.getTaskId() <= cursor
                        || row.getProjectId() == null || row.getProjectId() <= 0
                        || row.getTaskVersion() == null || row.getTaskVersion() < 0
                        || !taskIds.add(row.getTaskId())) {
                    throw corrupted("dashboard candidate projection is corrupted");
                }
                cursor = row.getTaskId();
                all.add(row);
            }
            if (batch.size() < BATCH_SIZE) return List.copyOf(all);
        }
    }

    private long countTodos(long tenantId, long actorId, PermissionFacts permissions,
                            List<CutoverDashboardCandidateRow> candidates) {
        List<CandidateNeed> needs = candidates.stream().map(row -> new CandidateNeed(
                row.getTaskId(), row.getProjectId(), row.getTaskVersion(), row.getCurrentStage(),
                row.getStageFactId(), row.getStageFactVersion())).toList();
        List<CutoverDashboardActionFacts> resolved = actionFactPort.inspectBatch(
                new BatchQuery(tenantId, actorId, needs));
        if (resolved == null || resolved.size() != candidates.size()) {
            throw corrupted("dashboard action facts are incomplete");
        }
        Map<Long, CutoverDashboardActionFacts.ActionFacts> factsByTaskId = new HashMap<>();
        for (CutoverDashboardActionFacts item : resolved) {
            if (item == null || item.taskId() == null || item.facts() == null
                    || factsByTaskId.putIfAbsent(item.taskId(), item.facts()) != null) {
                throw corrupted("dashboard action facts are corrupted");
            }
        }
        long count = 0;
        for (CutoverDashboardCandidateRow row : candidates) {
            CutoverDashboardActionFacts.ActionFacts facts = factsByTaskId.get(row.getTaskId());
            if (facts == null) throw corrupted("dashboard action fact identity is missing");
            CutoverDashboardCandidate candidate = new CutoverDashboardCandidate(row.getTaskId(),
                    row.getTaskOrigin(), row.getCurrentStage(), row.getTaskStatus(), row.getOwnerUserId(),
                    actorId, row.getManualGrade());
            HashSet<String> actions = new HashSet<>();
            actions.addAll(p2p3Policy.allowedActions(candidate, facts, permissions));
            actions.addAll(p4Policy.allowedActions(candidate, facts, permissions));
            actions.addAll(p5Policy.allowedActions(candidate, facts, permissions));
            actions.addAll(p6Policy.allowedActions(candidate, facts, permissions));
            if (actions.stream().anyMatch(TODO_ACTIONS::contains)) count++;
        }
        return count;
    }

    private static boolean isTodoCandidate(CutoverDashboardCandidateRow row) {
        return "NEW_PLATFORM".equals(row.getTaskOrigin()) && !"ARCHIVED".equals(row.getTaskStatus())
                && row.getCurrentStage() != null
                && Set.of("P2", "P3", "P4", "P5", "P6").contains(row.getCurrentStage());
    }

    private static CutoverDashboardOwnerFactException corrupted(String message) {
        return ownerCorrupted("CUT", message);
    }

    private static CutoverDashboardOwnerFactException ownerCorrupted(String ownerContext, String message) {
        return new CutoverDashboardOwnerFactException("OWNER_DATA_CORRUPTED", "OWNER_FACT_CORRUPTED",
                ownerContext, new IllegalStateException(message));
    }
}
