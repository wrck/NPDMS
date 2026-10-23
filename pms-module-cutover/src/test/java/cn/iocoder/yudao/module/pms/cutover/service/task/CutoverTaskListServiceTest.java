package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskListMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.CutoverTaskPageQuery;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardContext;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.service.task.query.CutoverTaskListQuery.ListKind.*;
import static cn.iocoder.yudao.module.pms.cutover.support.CutoverTestContexts.dashboard;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverTaskListServiceTest {
    private final CutoverTaskListMapper mapper = mock(CutoverTaskListMapper.class);
    private final CutoverDashboardQueryService dashboard = mock(CutoverDashboardQueryService.class);
    private final CutoverTaskQueryService tasks = mock(CutoverTaskQueryService.class);
    private final AdminUserApi users = mock(AdminUserApi.class);
    private final CutoverTaskListService service = new CutoverTaskListService(mapper, dashboard, tasks, users);
    private final CutoverDashboardContext context = dashboard();

    @Test
    void emptyPermissionScopeNeverQueriesTasksOrUsers() {
        scope(Set.of(), Map.of());
        when(dashboard.taskIds(any(), eq(TODO))).thenReturn(Set.of());
        assertThat(service.page(context, query(TODO)).getTotal()).isZero();
        verifyNoInteractions(mapper, tasks, users);
    }

    @Test
    void explicitProjectOutsideScopeIsRejectedBeforeQuery() {
        scope(Set.of(101L), Map.of());
        var query = CutoverTaskListQuery.builder().listKind(SELF_CREATED).projectId(201L).pageNo(1).pageSize(10).build();
        assertThatThrownBy(() -> service.page(context, query)).isInstanceOf(CutoverTaskApplicationException.class);
        verifyNoInteractions(mapper, tasks, users);
    }

    @Test
    void unavailableScopeDoesNotBecomeAnUnrestrictedList() {
        when(dashboard.scope(context)).thenThrow(new CutoverDashboardOwnerFactException(
                "OWNER_PROVIDER_UNAVAILABLE", "PROJECT_SCOPE_PROVIDER_UNAVAILABLE", "PROJ", new IllegalStateException("offline")));
        assertThatThrownBy(() -> service.page(context, query(ALL))).isInstanceOf(CutoverDashboardOwnerFactException.class);
        verifyNoInteractions(mapper, tasks, users);
    }

    @Test
    void emptyTodoSelectionDoesNotDropTheTaskFilter() {
        scope(Set.of(101L), Map.of());
        when(dashboard.taskIds(any(), eq(TODO))).thenReturn(Set.of());
        assertThat(service.page(context, query(TODO)).getList()).isEmpty();
        verifyNoInteractions(mapper, tasks, users);
    }

    @Test
    void selfCreatedSourceAndConditionsReachDatabaseBeforePagination() {
        scope(Set.of(101L), Map.of());
        when(mapper.selectTaskPage(any())).thenReturn(PageResult.empty());
        var query = CutoverTaskListQuery.builder().listKind(SELF_CREATED).projectId(101L)
                .currentStage("P4").taskStatus("PLAN_DRAFTING").pageNo(2).pageSize(10).build();
        service.page(context, query);
        var captured = ArgumentCaptor.forClass(CutoverTaskPageQuery.class);
        verify(mapper).selectTaskPage(captured.capture());
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
        verifyNoInteractions(users);
    }

    @Test
    void independentListKeepsAllSourcesAndArchivedRowsInTheDatabaseSelection() {
        scope(Set.of(101L), Map.of());
        when(mapper.selectTaskPage(any())).thenReturn(PageResult.empty());
        service.page(context, CutoverTaskListQuery.builder().listKind(ALL).taskStatus("ARCHIVED")
                .pageNo(3).pageSize(20).build());
        var captured = ArgumentCaptor.forClass(CutoverTaskPageQuery.class);
        verify(mapper).selectTaskPage(captured.capture());
        assertThat(captured.getValue()).satisfies(value -> {
            assertThat(value.intakeSourceType()).isNull();
            assertThat(value.taskIds()).isNull();
            assertThat(value.taskStatus()).isEqualTo("ARCHIVED");
            assertThat(value.pageParam().getPageNo()).isEqualTo(3);
        });
    }

    @Test
    void creatorNameComesFromStoredCreatorRatherThanTaskOwner() {
        scope(Set.of(101L), Map.of());
        row("8", false);
        var creator = new AdminUserRespDTO(); creator.setId(8L); creator.setNickname("实际创建人");
        when(users.getUserList(Set.of(8L))).thenReturn(List.of(creator));
        assertThat(service.page(context, query(SELF_CREATED)).getList()).singleElement()
                .satisfies(item -> assertThat(item.creatorName()).isEqualTo("实际创建人"));
        verify(users).getUserList(Set.of(8L));
    }

    @Test
    void initializerCreatorIsNotGuessedToBeOwner() {
        scope(Set.of(101L), Map.of());
        row("cutover-sample-initializer", false);
        assertThat(service.page(context, query(SELF_CREATED)).getList()).singleElement()
                .satisfies(item -> assertThat(item.creatorName()).isNull());
        verifyNoInteractions(users);
    }

    @Test
    void unboundedPaginationIsRejectedBeforeOwnerOrDatabaseCalls() {
        var query = CutoverTaskListQuery.builder().listKind(ALL).pageNo(1).pageSize(101).build();
        assertThatThrownBy(() -> service.page(context, query)).isInstanceOf(CutoverTaskApplicationException.class);
        verifyNoInteractions(dashboard, mapper, users);
    }

    @Test
    void roleTodoOutsideProjectScopeReturnsOnlyAuthorizedTaskAsApprovalOnly() {
        scope(Set.of(), Map.of(701L, 901L));
        when(dashboard.taskIds(any(), eq(TODO))).thenReturn(Set.of(701L, 702L));
        row("8", true);
        var result = service.page(context, query(TODO));
        var captured = ArgumentCaptor.forClass(CutoverTaskPageQuery.class);
        verify(mapper).selectTaskPage(captured.capture());
        assertThat(captured.getValue().visibleProjectIds()).isEmpty();
        assertThat(captured.getValue().approvalTaskIds()).containsExactly(701L);
        assertThat(captured.getValue().taskIds()).containsExactlyInAnyOrder(701L, 702L);
        assertThat(result.getList()).singleElement().satisfies(item -> {
            assertThat(item.task().approvalOnly()).isTrue();
            assertThat(item.approvalInstanceId()).isEqualTo(901L);
            assertThat(item.creatorName()).isNull();
        });
        verifyNoInteractions(users);
    }

    @Test
    void selfCreatedListCannotUseRoleTodoToExpandEmptyProjectScope() {
        scope(Set.of(), Map.of(701L, 901L));
        assertThat(service.page(context, query(SELF_CREATED)).getList()).isEmpty();
        verifyNoInteractions(mapper, tasks, users);
    }

    @Test
    void roleTodoKeepsApprovalRouteWhenItsProjectIsAlsoVisible() {
        scope(Set.of(101L), Map.of(701L, 901L));
        when(dashboard.taskIds(any(), eq(TODO))).thenReturn(Set.of(701L));
        var summary = row("8", true);
        service.page(context, query(TODO));
        verify(summary).withApprovalOnly(true);
        verifyNoInteractions(users);
    }

    private void scope(Set<Long> projects, Map<Long, Long> approvals) {
        when(dashboard.scope(context)).thenReturn(new CutoverDashboardQueryService.Scope(projects, approvals));
        // ALL/SELF_CREATED 的正式契约是 null，表示不额外限定任务 ID。
        when(dashboard.taskIds(any(), any())).thenReturn(null);
    }

    private CutoverTaskViews.Summary row(String creator, boolean approvalOnly) {
        var row = new CutoverTaskDO(); row.setId(701L); row.setTenantId(1L); row.setProjectId(101L);
        row.setOwnerUserId(9L); row.setCreator(creator);
        when(mapper.selectTaskPage(any())).thenReturn(new PageResult<>(List.of(row), 1L));
        var summary = mock(CutoverTaskViews.Summary.class);
        when(tasks.summary(row)).thenReturn(summary);
        when(summary.withApprovalOnly(approvalOnly)).thenReturn(summary);
        when(summary.approvalOnly()).thenReturn(approvalOnly);
        return summary;
    }

    private static CutoverTaskListQuery query(CutoverTaskListQuery.ListKind kind) {
        return CutoverTaskListQuery.builder().listKind(kind).pageNo(1).pageSize(10).build();
    }
}
