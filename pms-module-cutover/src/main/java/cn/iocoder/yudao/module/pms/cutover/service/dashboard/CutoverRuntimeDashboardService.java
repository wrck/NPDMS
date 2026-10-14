package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.context.CutoverTaskContext;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.query.CutoverDashboardCandidateQuery;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverRuntimeClosureQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverRuntimePlanQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverRuntimeTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/** 首页计数与钻取共用同一组授权任务及各阶段现行办理规则。 */
@Service
@RequiredArgsConstructor
public class CutoverRuntimeDashboardService {
    private final CutoverDashboardCandidateMapper candidates;
    private final CutoverProjectScopePort scopes;
    private final CutoverRuntimeTaskQueryService tasks;
    private final CutoverRuntimePlanQueryService plans;
    private final CutoverRuntimeApprovalQueryService approvals;
    private final CutoverRuntimeClosureQueryService closures;
    private final CutoverTaskRequestContext taskContext;
    private final CutoverPlanRequestContext planContext;
    private final CutoverApprovalRequestContext approvalContext;
    private final CutoverClosureRequestContext closureContext;

    public CutoverDashboardKpiView kpis() {
        var groups = groups();
        return new CutoverDashboardKpiView(groups.get("todo").size(), groups.get("archived").size(),
                groups.get("approving").size(), groups.get("rejected").size(), LocalDateTime.now());
    }

    public PageResult<CutoverTaskViews.Summary> page(String category, int page, int size) {
        if (!Set.of("todo", "archived", "approving", "rejected").contains(category)) throw new IllegalArgumentException("首页分类无效");
        var context = taskContext.current();
        var pagination = new PageParam();
        pagination.setPageNo(page); pagination.setPageSize(size);
        return tasks.pageForTaskIds(context, groups().get(category), pagination);
    }

    public Set<Long> taskIds(String category) {
        if (!Set.of("todo", "archived", "approving", "rejected").contains(category)) throw new IllegalArgumentException("首页分类无效");
        return Set.copyOf(groups().get(category));
    }

    private Map<String, Set<Long>> groups() {
        var context = taskContext.current();
        var groups = new LinkedHashMap<String, Set<Long>>();
        for (String name : List.of("todo", "archived", "approving", "rejected")) groups.put(name, new LinkedHashSet<>());
        var visible = scopes.resolveAllCurrent(context.actorId(), "ACTION_VIEW");
        var approval = approvalContext.current();
        var approvalTodos = new HashSet<Long>();
        if (approval.canQuery() && approval.canApprove()) {
            for (int page = 1; ; page++) {
                var pagination = new PageParam(); pagination.setPageNo(page); pagination.setPageSize(100);
                var rows = approvals.myTodos(approval, pagination);
                rows.list().forEach(row -> approvalTodos.add(row.taskId()));
                if (page * 100L >= rows.total()) break;
            }
        }
        if (visible.isEmpty() && approvalTodos.isEmpty()) return groups;
        long cursor = 0;
        while (true) {
            var rows = candidates.selectBatch(new CutoverDashboardCandidateQuery(context.tenantId(), visible, cursor, 500, approvalTodos));
            if (rows.isEmpty()) break;
            for (var row : rows) {
                cursor = row.getTaskId();
                if ("ARCHIVED".equals(row.getTaskStatus())) { groups.get("archived").add(row.getTaskId()); continue; }
                if ("P5".equals(row.getCurrentStage()) && "APPROVING".equals(row.getTaskStatus())) groups.get("approving").add(row.getTaskId());
                if ("P4".equals(row.getCurrentStage()) && "REJECTED".equals(row.getCurrentApprovalStatus())) groups.get("rejected").add(row.getTaskId());
                if (!"NEW_PLATFORM".equals(row.getTaskOrigin())) continue;
                if (approvalTodos.contains(row.getTaskId())) groups.get("todo").add(row.getTaskId());
                else if (Objects.equals(context.actorId(), row.getOwnerUserId()) && actionable(row, context)) groups.get("todo").add(row.getTaskId());
            }
            if (rows.size() < 500) break;
        }
        return groups;
    }

    private boolean actionable(CutoverDashboardCandidateRow row, CutoverTaskContext context) {
        return switch (row.getCurrentStage()) {
            case "P2", "P3" -> !tasks.detail(context, row.getTaskId()).allowedActions().isEmpty();
            case "P4" -> {
                var p = planContext.current();
                yield plans.detail(p, row.getTaskId())
                        .allowedActions().stream().anyMatch(Set.of("CREATE_DRAFT", "SAVE_DRAFT", "SUBMIT_PLAN", "REVISE_PLAN")::contains);
            }
            case "P6" -> {
                var c = closureContext.current();
                yield !closures.detail(c, row.getTaskId()).allowedActions().isEmpty();
            }
            default -> false;
        };
    }
}
