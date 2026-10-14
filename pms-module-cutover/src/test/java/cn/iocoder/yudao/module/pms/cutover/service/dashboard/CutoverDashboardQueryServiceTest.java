package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateRow;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery.ListKind;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import cn.iocoder.yudao.module.pms.cutover.support.CutoverTestContexts;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverDashboardQueryServiceTest {
    final CutoverDashboardCandidateMapper candidates = mock(CutoverDashboardCandidateMapper.class);
    final CutoverProjectScopePort scopes = mock(CutoverProjectScopePort.class);
    final CutoverTaskQueryService tasks = mock(CutoverTaskQueryService.class);
    final CutoverPlanQueryService plans = mock(CutoverPlanQueryService.class);
    final CutoverApprovalQueryService approvals = mock(CutoverApprovalQueryService.class);
    final CutoverClosureQueryService closures = mock(CutoverClosureQueryService.class);
    final CutoverDashboardQueryService service = new CutoverDashboardQueryService(candidates, scopes, tasks, plans, approvals, closures);
    final cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardContext context = CutoverTestContexts.dashboard();

    @Test void aggregatesOverlappingMetricsUsingCurrentStageActions() {
        scope(Set.of(101L));
        when(approvals.myTodos(any(), any())).thenReturn(new CutoverApprovalViews.Page<>(
                List.of(new CutoverApprovalViews.TodoItem(202L, 0L, 2L, 101L, "CUT-2", "待审批", "A", 1, "INITIATOR", null)), 1, 1, 100));
        when(candidates.selectBatch(any())).thenReturn(List.of(row(1L, "P2", "GRADE_CONFIRMING", null),
                row(2L, "P5", "APPROVING", "PENDING"), row(3L, "P4", "PLAN_DRAFTING", "REJECTED"),
                row(4L, "P6", "ARCHIVED", "APPROVED"), row(5L, "P4", "PLAN_DRAFTING", "REJECTED")));
        var task = mock(CutoverTaskViews.Detail.class);
        when(task.allowedActions()).thenReturn(List.of("SAVE_ASSESSMENT"));
        when(tasks.detail(context.task(), 1L)).thenReturn(task);
        var editable = mock(CutoverPlanView.class);
        when(editable.allowedActions()).thenReturn(List.of("REVISE_PLAN"));
        when(plans.detail(context.plan(), 3L)).thenReturn(editable);
        var readonly = mock(CutoverPlanView.class);
        when(readonly.allowedActions()).thenReturn(List.of("DOWNLOAD_DRAFT"));
        when(plans.detail(context.plan(), 5L)).thenReturn(readonly);
        LocalDateTime before = LocalDateTime.now();
        var result = service.statistics(context);
        assertThat(result.todoCount()).isEqualTo(3);
        assertThat(result.archivedCount()).isEqualTo(1);
        assertThat(result.approvingCount()).isEqualTo(1);
        assertThat(result.rejectedPendingModificationCount()).isEqualTo(2);
        assertThat(result.generatedAt()).isBetween(before, LocalDateTime.now());
        verifyNoInteractions(closures);
    }

    @Test void emptyProjectAndApprovalScopeDoesNotQueryCandidatesOrStageDetails() {
        scope(Set.of());
        var result = service.statistics(context);
        assertThat(result.todoCount()).isZero();
        assertThat(result.archivedCount()).isZero();
        assertThat(result.approvingCount()).isZero();
        assertThat(result.rejectedPendingModificationCount()).isZero();
        verifyNoInteractions(candidates, tasks, plans, closures);
    }

    @Test void sharedRoleTodoSurvivesAnEmptyProjectScopeWithoutLoadingFullTask() {
        scope(Set.of());
        when(approvals.myTodos(any(), any())).thenReturn(new CutoverApprovalViews.Page<>(
                List.of(new CutoverApprovalViews.TodoItem(202L, 0L, 2L, 101L, "CUT-2", "待审批", "A", 3, "SECOND_LINE", null)), 1, 1, 100));
        when(candidates.selectBatch(any())).thenReturn(List.of(row(2L, "P5", "APPROVING", "PENDING")));
        var resolved = service.scope(context);
        assertThat(service.taskIds(new CutoverDashboardQueryService.Selection(context, resolved), ListKind.TODO)).containsExactly(2L);
        verifyNoInteractions(tasks, plans, closures);
    }

    @Test void outOfScopeOrDuplicateCandidatesFailClosed() {
        scope(Set.of(101L));
        var foreign = row(1L, "P6", "ARCHIVED", "APPROVED"); foreign.setProjectId(999L);
        when(candidates.selectBatch(any())).thenReturn(List.of(foreign));
        assertThatThrownBy(() -> service.statistics(context)).isInstanceOf(CutoverDashboardOwnerFactException.class);
        var same = row(1L, "P6", "ARCHIVED", "APPROVED");
        when(candidates.selectBatch(any())).thenReturn(List.of(same, same));
        assertThatThrownBy(() -> service.statistics(context)).isInstanceOf(CutoverDashboardOwnerFactException.class);
    }

    private void scope(Set<Long> projects) {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(projects);
        when(approvals.myTodos(any(), any())).thenReturn(new CutoverApprovalViews.Page<>(List.of(), 0, 1, 100));
    }
    private static CutoverDashboardCandidateRow row(Long id, String stage, String status, String approvalStatus) {
        var row = new CutoverDashboardCandidateRow(); row.setTaskId(id); row.setProjectId(101L);
        row.setTaskOrigin("NEW_PLATFORM"); row.setCurrentStage(stage); row.setTaskStatus(status);
        row.setOwnerUserId(9L); row.setTaskVersion(2L); row.setCurrentApprovalStatus(approvalStatus);
        return row;
    }
}
