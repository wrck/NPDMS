package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverWorkbenchTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverWorkbenchTaskPageQuery;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverRuntimeDashboardService;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.query.CutoverWorkbenchTaskListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.view.CutoverTaskViews;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.service.taskv2.query.CutoverWorkbenchTaskListQuery.ListKind.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverWorkbenchTaskQueryServiceTest {
    private final CutoverWorkbenchTaskMapper mapper = mock(CutoverWorkbenchTaskMapper.class);
    private final CutoverProjectScopePort scopes = mock(CutoverProjectScopePort.class);
    private final CutoverRuntimeDashboardService dashboard = mock(CutoverRuntimeDashboardService.class);
    private final CutoverRuntimeTaskQueryService tasks = mock(CutoverRuntimeTaskQueryService.class);
    private final AdminUserApi users = mock(AdminUserApi.class);
    private final cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience audience =
            mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience.class);
    private final CutoverWorkbenchTaskQueryService service = new CutoverWorkbenchTaskQueryService(mapper, scopes, dashboard, tasks, users, audience);
    private final CutoverTaskContext context = new CutoverTaskContext(1L, 9L, "workbench-query", false, false, false, false, false);

    @Test
    void emptyPermissionScopeNeverQueriesTasksOrUsers() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of());
        when(dashboard.taskIds("todo")).thenReturn(Set.of());
        assertThat(service.page(context, query(TODO)).getTotal()).isZero();
        verify(dashboard).taskIds("todo");
        verifyNoInteractions(mapper, tasks, users, audience);
    }

    @Test
    void explicitProjectOutsideScopeIsRejectedBeforeQuery() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of(101L));
        var query = CutoverWorkbenchTaskListQuery.builder().listKind(SELF_CREATED).projectId(201L).pageNo(1).pageSize(10).build();
        assertThatThrownBy(() -> service.page(context, query)).isInstanceOf(CutoverTaskApplicationException.class);
        verifyNoInteractions(mapper, dashboard, tasks, users);
    }

    @Test
    void unavailableScopeDoesNotBecomeAnUnrestrictedList() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(null);
        assertThatThrownBy(() -> service.page(context, query(SELF_CREATED))).isInstanceOf(CutoverTaskApplicationException.class);
        verifyNoInteractions(mapper, dashboard, tasks, users);
    }

    @Test
    void emptyTodoSelectionDoesNotDropTheTaskFilter() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of(101L));
        when(dashboard.taskIds("todo")).thenReturn(Set.of());
        assertThat(service.page(context, query(TODO)).getList()).isEmpty();
        verifyNoInteractions(mapper, tasks, users);
    }

    @Test
    void selfCreatedSourceAndConditionsReachDatabaseBeforePagination() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of(101L));
        when(mapper.selectWorkbenchPage(any())).thenReturn(PageResult.empty());
        var query = CutoverWorkbenchTaskListQuery.builder().listKind(SELF_CREATED).projectId(101L)
                .currentStage("P4").taskStatus("PLAN_DRAFTING").pageNo(2).pageSize(10).build();
        service.page(context, query);
        var captured = ArgumentCaptor.forClass(CutoverWorkbenchTaskPageQuery.class);
        verify(mapper).selectWorkbenchPage(captured.capture());
        assertThat(captured.getValue()).satisfies(value -> {
            assertThat(value.tenantId()).isEqualTo(1L);
            assertThat(value.visibleProjectIds()).containsExactly(101L);
            assertThat(value.intakeSourceType()).isEqualTo("SELF_CREATED");
            assertThat(value.taskIds()).isNull();
            assertThat(value.projectId()).isEqualTo(101L);
            assertThat(value.currentStage()).isEqualTo("P4");
            assertThat(value.taskStatus()).isEqualTo("PLAN_DRAFTING");
            assertThat(value.pageParam().getPageNo()).isEqualTo(2);
        });
        verifyNoInteractions(dashboard, users);
    }

    @Test
    void creatorNameComesFromStoredCreatorRatherThanTaskOwner() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of(101L));
        var row = new CutoverTaskDO(); row.setId(701L); row.setProjectId(101L); row.setOwnerUserId(9L); row.setCreator("8");
        when(mapper.selectWorkbenchPage(any())).thenReturn(new PageResult<>(List.of(row), 1L));
        var summary = mock(CutoverTaskViews.Summary.class);
        when(tasks.summary(row)).thenReturn(summary);
        when(summary.withApprovalOnly(false)).thenReturn(summary);
        var creator = new AdminUserRespDTO(); creator.setId(8L); creator.setNickname("实际创建人");
        when(users.getUserList(Set.of(8L))).thenReturn(List.of(creator));
        assertThat(service.page(context, query(SELF_CREATED)).getList()).singleElement()
                .satisfies(item -> assertThat(item.creatorName()).isEqualTo("实际创建人"));
        verify(users).getUserList(Set.of(8L));
    }

    @Test
    void initializerCreatorIsNotGuessedToBeOwner() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of(101L));
        var row = new CutoverTaskDO(); row.setId(701L); row.setProjectId(101L); row.setOwnerUserId(9L); row.setCreator("cutover-sample-initializer");
        var summary = mock(CutoverTaskViews.Summary.class);
        when(tasks.summary(row)).thenReturn(summary);
        when(summary.withApprovalOnly(false)).thenReturn(summary);
        when(mapper.selectWorkbenchPage(any())).thenReturn(new PageResult<>(List.of(row), 1L));
        assertThat(service.page(context, query(SELF_CREATED)).getList()).singleElement()
                .satisfies(item -> assertThat(item.creatorName()).isNull());
        verifyNoInteractions(users);
    }

    @Test
    void unboundedPaginationIsRejectedBeforeOwnerOrDatabaseCalls() {
        var query = CutoverWorkbenchTaskListQuery.builder().listKind(SELF_CREATED).pageNo(1).pageSize(101).build();
        assertThatThrownBy(() -> service.page(context, query)).isInstanceOf(CutoverTaskApplicationException.class);
        verifyNoInteractions(scopes, dashboard, mapper, users);
    }

    private static CutoverWorkbenchTaskListQuery query(CutoverWorkbenchTaskListQuery.ListKind kind) {
        return CutoverWorkbenchTaskListQuery.builder().listKind(kind).pageNo(1).pageSize(10).build();
    }

    @Test
    void roleTodoOutsideProjectScopeReturnsOnlyAuthorizedTaskAsApprovalOnly() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of());
        when(dashboard.taskIds("todo")).thenReturn(Set.of(701L, 702L));
        when(audience.authorizedTaskIds(any())).thenReturn(Set.of(701L));
        var row = new CutoverTaskDO(); row.setId(701L); row.setProjectId(101L);
        var summary = mock(CutoverTaskViews.Summary.class);
        when(summary.withApprovalOnly(true)).thenReturn(summary);
        when(summary.approvalOnly()).thenReturn(true);
        when(tasks.summary(row)).thenReturn(summary);
        when(mapper.selectWorkbenchPage(any())).thenAnswer(invocation -> {
            CutoverWorkbenchTaskPageQuery selection = invocation.getArgument(0);
            assertThat(selection.visibleProjectIds()).isEmpty();
            assertThat(selection.approvalTaskIds()).containsExactly(701L);
            assertThat(selection.taskIds()).containsExactlyInAnyOrder(701L, 702L);
            return new PageResult<>(List.of(row), 1L);
        });
        var result = service.page(context, query(TODO));
        assertThat(result.getList()).singleElement().satisfies(item -> assertThat(item.task().approvalOnly()).isTrue());
        verify(audience).authorizedTaskIds(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience.Selection(
                1L, 9L, Set.of(701L, 702L)));
        verifyNoInteractions(users);
    }

    @Test
    void selfCreatedListCannotUseRoleTodoToExpandEmptyProjectScope() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of());
        assertThat(service.page(context, query(SELF_CREATED)).getList()).isEmpty();
        verifyNoInteractions(mapper, dashboard, audience, tasks, users);
    }

    @Test
    void roleTodoKeepsApprovalRouteWhenItsProjectIsAlsoVisible() {
        when(scopes.resolveAllCurrent(9L, "ACTION_VIEW")).thenReturn(Set.of(101L));
        when(dashboard.taskIds("todo")).thenReturn(Set.of(701L));
        when(audience.authorizedTaskIds(any())).thenReturn(Set.of(701L));
        var row = new CutoverTaskDO(); row.setId(701L); row.setProjectId(101L);
        var summary = mock(CutoverTaskViews.Summary.class);
        when(summary.withApprovalOnly(anyBoolean())).thenReturn(summary);
        when(tasks.summary(row)).thenReturn(summary);
        when(mapper.selectWorkbenchPage(any())).thenReturn(new PageResult<>(List.of(row), 1L));

        service.page(context, query(TODO));

        verify(summary).withApprovalOnly(true);
        verifyNoInteractions(users);
    }
}
