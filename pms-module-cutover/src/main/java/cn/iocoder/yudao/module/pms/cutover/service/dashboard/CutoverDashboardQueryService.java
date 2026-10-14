package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateRow;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.query.CutoverDashboardCandidateQuery;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardContext;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery.ListKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 统计和分类列表复用授权范围、审批待办及各阶段现行办理规则。 */
@Service
@RequiredArgsConstructor
public class CutoverDashboardQueryService {
    private static final int BATCH_SIZE = 500;
    private final CutoverDashboardCandidateMapper candidates;
    private final CutoverProjectScopePort scopes;
    private final CutoverTaskQueryService tasks;
    private final CutoverPlanQueryService plans;
    private final CutoverApprovalQueryService approvals;
    private final CutoverClosureQueryService closures;

    public CutoverDashboardKpiView statistics(CutoverDashboardContext context) {
        var groups = groups(new Selection(context, scope(context)));
        return new CutoverDashboardKpiView(groups.get(ListKind.TODO).size(), groups.get(ListKind.ARCHIVED).size(),
                groups.get(ListKind.APPROVING).size(), groups.get(ListKind.REJECTED).size(), LocalDateTime.now());
    }

    public Scope scope(CutoverDashboardContext context) {
        Set<Long> visible;
        try {
            visible = scopes.resolveAllCurrent(context.task().actorId(), "ACTION_VIEW");
        } catch (CutoverOwnerFactException failure) {
            boolean unavailable = failure.code() == CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE;
            throw new CutoverDashboardOwnerFactException(unavailable ? "OWNER_PROVIDER_UNAVAILABLE" : "OWNER_DATA_CORRUPTED",
                    unavailable ? "PROJECT_SCOPE_PROVIDER_UNAVAILABLE" : "OWNER_FACT_CORRUPTED", "PROJ", failure);
        }
        if (visible == null || visible.stream().anyMatch(id -> id == null || id <= 0))
            throw corrupted("PROJ", "项目范围事实无效");
        Map<Long, Long> approvalInstances = new LinkedHashMap<>();
        if (context.approval().canQuery() && context.approval().canApprove()) {
            for (int page = 1; ; page++) {
                var pagination = new PageParam();
                pagination.setPageNo(page); pagination.setPageSize(100);
                var rows = approvals.myTodos(context.approval(), pagination);
                rows.list().forEach(row -> approvalInstances.put(row.taskId(), row.approvalInstanceId()));
                if (page * 100L >= rows.total()) break;
                if (rows.list().isEmpty()) throw corrupted("CUT", "审批待办分页事实不完整");
            }
        }
        return new Scope(visible, approvalInstances);
    }

    public Set<Long> taskIds(Selection selection, ListKind kind) {
        if (kind == ListKind.ALL || kind == ListKind.SELF_CREATED) return null;
        return Set.copyOf(groups(selection).get(kind));
    }

    private Map<ListKind, Set<Long>> groups(Selection selection) {
        var context = selection.context();
        var scope = selection.scope();
        Map<ListKind, Set<Long>> groups = new EnumMap<>(ListKind.class);
        for (var kind : List.of(ListKind.TODO, ListKind.ARCHIVED, ListKind.APPROVING, ListKind.REJECTED))
            groups.put(kind, new LinkedHashSet<>());
        if (scope.visibleProjectIds().isEmpty() && scope.approvalInstances().isEmpty()) return groups;
        Set<Long> seen = new HashSet<>();
        long cursor = 0;
        while (true) {
            var rows = candidates.selectBatch(new CutoverDashboardCandidateQuery(context.task().tenantId(),
                    scope.visibleProjectIds(), cursor, BATCH_SIZE, scope.approvalInstances().keySet()));
            if (rows == null) throw corrupted("CUT", "割接统计候选批次缺失");
            for (var row : rows) {
                if (row == null || row.getTaskId() == null || row.getTaskId() <= cursor
                        || row.getProjectId() == null || row.getTaskVersion() == null || row.getTaskVersion() < 0
                        || !seen.add(row.getTaskId()) || (!scope.visibleProjectIds().contains(row.getProjectId())
                        && !scope.approvalInstances().containsKey(row.getTaskId())))
                    throw corrupted("CUT", "割接统计候选事实越界或无效");
                cursor = row.getTaskId();
                if ("ARCHIVED".equals(row.getTaskStatus())) {
                    groups.get(ListKind.ARCHIVED).add(row.getTaskId()); continue;
                }
                if ("P5".equals(row.getCurrentStage()) && "APPROVING".equals(row.getTaskStatus()))
                    groups.get(ListKind.APPROVING).add(row.getTaskId());
                if ("P4".equals(row.getCurrentStage()) && "PLAN_DRAFTING".equals(row.getTaskStatus())
                        && "REJECTED".equals(row.getCurrentApprovalStatus()))
                    groups.get(ListKind.REJECTED).add(row.getTaskId());
                if (!"NEW_PLATFORM".equals(row.getTaskOrigin())) continue;
                if (scope.approvalInstances().containsKey(row.getTaskId())
                        || Objects.equals(context.task().actorId(), row.getOwnerUserId())
                        && scope.visibleProjectIds().contains(row.getProjectId()) && actionable(row, context))
                    groups.get(ListKind.TODO).add(row.getTaskId());
            }
            if (rows.size() < BATCH_SIZE) break;
        }
        return groups;
    }

    private boolean actionable(CutoverDashboardCandidateRow row, CutoverDashboardContext context) {
        if (row.getCurrentStage() == null) return false;
        return switch (row.getCurrentStage()) {
            case "P2", "P3" -> !tasks.detail(context.task(), row.getTaskId()).allowedActions().isEmpty();
            case "P4" -> plans.detail(context.plan(), row.getTaskId()).allowedActions().stream()
                    .anyMatch(Set.of("CREATE_DRAFT", "SAVE_DRAFT", "SUBMIT_PLAN", "REVISE_PLAN")::contains);
            case "P6" -> !closures.detail(context.closure(), row.getTaskId()).allowedActions().isEmpty();
            default -> false;
        };
    }

    private static CutoverDashboardOwnerFactException corrupted(String owner, String message) {
        return new CutoverDashboardOwnerFactException("OWNER_DATA_CORRUPTED", "OWNER_FACT_CORRUPTED",
                owner, new IllegalStateException(message));
    }

    public record Scope(Set<Long> visibleProjectIds, Map<Long, Long> approvalInstances) {
        public Scope {
            visibleProjectIds = Set.copyOf(visibleProjectIds);
            approvalInstances = Map.copyOf(approvalInstances);
        }
    }
    public record Selection(CutoverDashboardContext context, Scope scope) { }
}
