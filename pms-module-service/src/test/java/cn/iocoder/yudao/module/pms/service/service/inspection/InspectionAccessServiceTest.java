package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.InspectionTaskMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InspectionAccessServiceTest {
    private static final Long TENANT_ID = 1L;
    private static final Long ACTOR_ID = 7L;
    private static final Long PROJECT_ID = 20L;
    private static final Long TREE_VERSION = 87L;
    private static final Long PROJECT_VERSION = 41L;
    private static final Long TASK_ID = 10L;
    private static final Long TASK_VERSION = 5L;
    private static final String QUERY_PERMISSION = "pms:inspection-task:query";
    private static final String ARCHIVE_PERMISSION = "pms:inspection-task:archive";
    private static final String ANNOTATE_PERMISSION = "pms:inspection-task:annotate";
    private static final InspectionTaskRows TASK_ROWS = new InspectionTaskRows(TENANT_ID, TASK_ID);

    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class, this::answerScope);
    private final ProjectBusinessContextApi projects = mock(ProjectBusinessContextApi.class, this::answerOwner);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final AdminUserApi users = mock(AdminUserApi.class);
    private final InspectionTaskMapper tasks = mock(InspectionTaskMapper.class);
    private final InspectionAccessService service = new InspectionAccessService(scopes, projects, permissions, users, tasks);
    private final InspectionRequestContext context = InspectionRequestContext.builder().tenantId(TENANT_ID).actorId(ACTOR_ID)
            .expectedVersion(TASK_VERSION).idempotencyKey("access-test").correlationId("access-test").build();
    private final ProjectBusinessContextApi.Context project = ProjectBusinessContextApi.Context.builder()
            .tenantId(TENANT_ID).projectId(PROJECT_ID).projectVersion(PROJECT_VERSION).scopeVersion(TREE_VERSION)
            .projectCode("P20").projectName("巡检项目").build();
    private ProjectScopeResult currentScope;
    private List<Long> managerUserIds;

    @BeforeEach
    void setUpCurrentScope() {
        TenantContextHolder.setTenantId(TENANT_ID);
        currentScope = new ProjectScopeResult(19L, TREE_VERSION, Set.of(PROJECT_ID), Set.of(19L));
        managerUserIds = List.of(99L);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void projectReadResolvesTheActorsCurrentTreeAndPassesTheCompleteOwnerQuery() {
        assertSame(project, service.project(projectAccess(false)));

        var order = inOrder(scopes, projects);
        order.verify(scopes).resolveCurrent(currentScopeQuery());
        order.verify(projects).inspect(ownerQuery());
        verify(projects, never()).lockAndRevalidate(any());
    }

    @Test
    void projectWriteLocksUsingBothTheInspectedProjectVersionAndTheTreeVersion() {
        assertSame(project, service.project(projectAccess(true)));

        var order = inOrder(scopes, projects);
        order.verify(scopes).resolveCurrent(currentScopeQuery());
        order.verify(projects).inspect(ownerQuery());
        order.verify(projects).lockAndRevalidate(ownerRevalidation());
    }

    @Test
    void managerReadPassesTheVersionedQueryAndRequiresTheActorInTheReturnedManagers() {
        managerUserIds = List.of(ACTOR_ID, 99L);
        assertTrue(service.manager(projectAccess(false)));
        managerUserIds = List.of(99L);
        assertFalse(service.manager(projectAccess(false)));

        verify(projects, times(2)).inspect(ownerQuery());
        verify(projects, times(2)).serviceManagers(ownerQuery());
        verify(projects, never()).lockServiceManagers(any());
    }

    @Test
    void managerWriteRevalidatesBothVersionsAndUsesTheLockedManagerMembership() {
        managerUserIds = List.of(ACTOR_ID);
        assertTrue(service.manager(projectAccess(true)));

        var order = inOrder(scopes, projects);
        order.verify(scopes).resolveCurrent(currentScopeQuery());
        order.verify(projects).inspect(ownerQuery());
        order.verify(projects).lockServiceManagers(ownerRevalidation());
        verify(projects, never()).serviceManagers(any());
    }

    @Test
    void absentEmptyAndPlaceholderOnlyScopesAreRejectedBeforeTheProjectOwnerIsCalled() {
        var deniedScopes = new ProjectScopeResult[] {
                null,
                new ProjectScopeResult(19L, TREE_VERSION, Set.of(), Set.of()),
                new ProjectScopeResult(19L, TREE_VERSION, Set.of(21L), Set.of(PROJECT_ID))
        };
        for (var deniedScope : deniedScopes) {
            currentScope = deniedScope;
            var projectFailure = assertThrows(InspectionException.class, () -> service.project(projectAccess(false)));
            assertEquals(InspectionException.Reason.FORBIDDEN, projectFailure.getReason());
            var managerFailure = assertThrows(InspectionException.class, () -> service.manager(projectAccess(true)));
            assertEquals(InspectionException.Reason.FORBIDDEN, managerFailure.getReason());
        }

        verifyNoInteractions(projects);
    }

    @Test
    void ownerTreeAndProjectVersionConflictsAreNotConvertedToSuccessfulAccessOrFalseMembership() {
        // Owner conflict codes are opaque to SRV; only the public framework exception type is imported.
        var treeConflict = new ServiceException(1_014_024_030, "项目树版本冲突");
        doAnswer(call -> { assertEquals(ownerQuery(), call.getArgument(0)); throw treeConflict; })
                .when(projects).inspect(any(ProjectBusinessContextApi.Query.class));

        assertSame(treeConflict, assertThrows(ServiceException.class, () -> service.project(projectAccess(false))));
        assertSame(treeConflict, assertThrows(ServiceException.class, () -> service.manager(projectAccess(false))));

        reset(projects); // Restore the strict default Answer before exercising the locking Owner endpoints.
        var projectConflict = new ServiceException(1_014_024_014, "Project版本冲突");
        doAnswer(call -> { assertEquals(ownerRevalidation(), call.getArgument(0)); throw projectConflict; })
                .when(projects).lockAndRevalidate(any(ProjectBusinessContextApi.Revalidation.class));
        doAnswer(call -> { assertEquals(ownerRevalidation(), call.getArgument(0)); throw projectConflict; })
                .when(projects).lockServiceManagers(any(ProjectBusinessContextApi.Revalidation.class));

        assertSame(projectConflict, assertThrows(ServiceException.class, () -> service.project(projectAccess(true))));
        assertSame(projectConflict, assertThrows(ServiceException.class, () -> service.manager(projectAccess(true))));
    }

    @Test
    void fullProjectScopeStillRequiresEitherTaskOwnershipOrCurrentManagerMembershipForReading() {
        when(permissions.hasAnyPermissions(ACTOR_ID, QUERY_PERMISSION)).thenReturn(true);
        var task = task();
        when(tasks.selectById(TASK_ID)).thenReturn(task);
        var request = new InspectionAccessService.TaskAccess(context, TASK_ID, QUERY_PERMISSION, false, false);

        assertSame(task, service.task(request));
        task.setOwnerUserId(99L);
        var outsider = assertThrows(InspectionException.class, () -> service.task(request));
        assertEquals(InspectionException.Reason.FORBIDDEN, outsider.getReason());
        managerUserIds = List.of(ACTOR_ID);
        assertSame(task, service.task(request));

        verify(tasks, never()).selectForUpdate(any());
    }

    @Test
    void ownerCannotUseManagerOnlyActionsAndOwnershipIsCheckedAgainAfterTheTaskLock() {
        when(permissions.hasAnyPermissions(ACTOR_ID, ARCHIVE_PERMISSION)).thenReturn(true);
        when(permissions.hasAnyPermissions(ACTOR_ID, ANNOTATE_PERMISSION)).thenReturn(true);
        var snapshot = task();
        when(tasks.selectById(TASK_ID)).thenReturn(snapshot);
        var managerOnly = new InspectionAccessService.TaskAccess(context, TASK_ID, ARCHIVE_PERMISSION, true, true);

        var notManager = assertThrows(InspectionException.class, () -> service.task(managerOnly));
        assertEquals(InspectionException.Reason.FORBIDDEN, notManager.getReason());
        verify(tasks, never()).selectForUpdate(any());

        var reassigned = task(); reassigned.setOwnerUserId(99L); reassigned.setVersion(6L);
        when(tasks.selectForUpdate(TASK_ROWS)).thenReturn(reassigned);
        var formerOwner = assertThrows(InspectionException.class, () -> service.task(
                new InspectionAccessService.TaskAccess(context, TASK_ID, ANNOTATE_PERMISSION, true, false)));
        assertEquals(InspectionException.Reason.FORBIDDEN, formerOwner.getReason());

        managerUserIds = List.of(ACTOR_ID);
        var current = task(); current.setOwnerUserId(99L);
        when(tasks.selectForUpdate(TASK_ROWS)).thenReturn(current);
        assertSame(current, service.task(managerOnly));
    }

    @Test
    void fullProjectScopeCannotReplaceTheRequiredTaskPermission() {
        var failure = assertThrows(InspectionException.class, () -> service.task(
                new InspectionAccessService.TaskAccess(context, TASK_ID, QUERY_PERMISSION, false, false)));

        assertEquals(InspectionException.Reason.FORBIDDEN, failure.getReason());
        verifyNoInteractions(scopes, projects, tasks);
    }

    private Object answerScope(InvocationOnMock call) throws Throwable {
        if ("resolveCurrent".equals(call.getMethod().getName())) {
            assertEquals(currentScopeQuery(), call.getArgument(0), "Resolve the authenticated actor's anchored VIEW scope");
            return currentScope;
        }
        return Answers.RETURNS_DEFAULTS.answer(call);
    }

    private Object answerOwner(InvocationOnMock call) throws Throwable {
        return switch (call.getMethod().getName()) {
            case "inspect" -> {
                assertEquals(ownerQuery(), call.getArgument(0), "Owner inspect requires the resolved tree version");
                yield project;
            }
            case "serviceManagers" -> {
                assertEquals(ownerQuery(), call.getArgument(0), "Manager reads require the same versioned query");
                yield new ProjectBusinessContextApi.ServiceManagers(PROJECT_VERSION, managerUserIds);
            }
            case "lockAndRevalidate" -> {
                assertEquals(ownerRevalidation(), call.getArgument(0), "Project writes must keep tree and project versions");
                yield project;
            }
            case "lockServiceManagers" -> {
                assertEquals(ownerRevalidation(), call.getArgument(0), "Locked manager reads must keep both versions");
                yield new ProjectBusinessContextApi.ServiceManagers(PROJECT_VERSION, managerUserIds);
            }
            default -> Answers.RETURNS_DEFAULTS.answer(call);
        };
    }

    private InspectionAccessService.ProjectAccess projectAccess(boolean lock) {
        return new InspectionAccessService.ProjectAccess(context, PROJECT_ID, lock);
    }

    private ProjectCurrentScopeQuery currentScopeQuery() {
        return new ProjectCurrentScopeQuery(TENANT_ID, ACTOR_ID, PROJECT_ID, ProjectScopeApi.ACTION_VIEW);
    }

    private ProjectBusinessContextApi.Query ownerQuery() {
        return new ProjectBusinessContextApi.Query(TENANT_ID, ACTOR_ID, PROJECT_ID, TREE_VERSION);
    }

    private ProjectBusinessContextApi.Revalidation ownerRevalidation() {
        return new ProjectBusinessContextApi.Revalidation(ownerQuery(), PROJECT_VERSION);
    }

    private InspectionTaskDO task() {
        var task = new InspectionTaskDO();
        task.setId(TASK_ID); task.setTenantId(TENANT_ID); task.setProjectId(PROJECT_ID);
        task.setOwnerUserId(ACTOR_ID); task.setVersion(TASK_VERSION); task.setStatusCode("CLOSED");
        return task;
    }
}
