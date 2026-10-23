package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceShipmentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionStateCount;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.ServiceIssueSummary;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskPageQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.ServiceIssueWorkbenchQuery;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRuleQueryService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionTaskQueryServiceTest {
    private static final Long TENANT_ID = 7L;
    private static final Long ACTOR_ID = 13L;
    private static final Long OWNER_ID = 17L;
    private static final Long CREATOR_ID = 23L;
    private static final Set<Long> VISIBLE = Set.of(101L, 102L, 103L);
    private static final Set<Long> MANAGED = Set.of(102L);
    private static final ProjectBusinessContextApi.AccessScope SCOPE =
            new ProjectBusinessContextApi.AccessScope(VISIBLE, MANAGED);
    private static final InspectionRequestContext CONTEXT = InspectionRequestContext.builder()
            .tenantId(TENANT_ID).actorId(ACTOR_ID).correlationId("query-test").build();
    private static final String TASK_QUERY = "pms:inspection-task:query";
    private static final String ISSUE_QUERY = "pms:service-issue:query";

    @Mock private InspectionAccessService access;
    @Mock private InspectionTaskMapper tasks;
    @Mock private InspectionTaskDeviceMapper devices;
    @Mock private InspectionRuleSelectionMapper selections;
    @Mock private InspectionAttemptMapper attempts;
    @Mock private InspectionExecutionMapper executions;
    @Mock private InspectionCollectionReceiptMapper collectionReceipts;
    @Mock private InspectionDetectionResultMapper results;
    @Mock private InspectionReportRevisionMapper reports;
    @Mock private InspectionReportResultMapper reportResults;
    @Mock private InspectionReportFileMapper reportFiles;
    @Mock private InspectionAnnotationMapper annotations;
    @Mock private ServiceIssueMapper issues;
    @Mock private ServiceIssueRemediationMapper remediations;
    @Mock private InspectionEvidenceMapper evidence;
    @Mock private InspectionTaskEventMapper events;
    @Mock private InspectionRuleFeedbackMapper ruleFeedback;
    @Mock private InspectionRuleQueryService rules;
    @Mock private ProjectDeviceShipmentApi shipments;
    @Mock private ProjectBusinessContextApi projectContexts;
    @Mock private AdminUserApi users;
    @Mock private FileArtifactApi files;
    @Mock private PermissionApi permissions;
    @InjectMocks private InspectionTaskQueryService service;

    @Test
    void deviceOptionsUseAuthorizedProjectShipments() {
        when(shipments.pageDevices(any())).thenReturn(new ProjectDeviceShipmentApi.DevicePage(
                List.of(new ProjectDeviceShipmentApi.Device(501L, "SHIPPED-501", "CODE", "MODEL")), 1));
        var options = service.devices(new InspectionTaskQueryService.DeviceOptions(CONTEXT, 101L));
        assertEquals(List.of(501L), options.stream().map(InspectionTaskQueryService.DeviceOption::id).toList());
        assertEquals("SHIPPED-501", options.getFirst().serialNumber());
        var order = inOrder(access, shipments);
        order.verify(access).permission(CONTEXT, TASK_QUERY);
        order.verify(access).project(new InspectionAccessService.ProjectAccess(CONTEXT, 101L, false));
        order.verify(shipments).pageDevices(argThat(q -> q.tenantId().equals(TENANT_ID)
                && q.actorId().equals(ACTOR_ID) && q.projectId().equals(101L)
                && q.pageNo() == 1 && q.pageSize() == 100));
    }

    @Test
    void forbiddenProjectNeverQueriesShipmentCandidates() {
        doThrow(new InspectionException(InspectionException.Reason.FORBIDDEN, "无项目权限"))
                .when(access).project(any());
        assertThrows(InspectionException.class,
                () -> service.devices(new InspectionTaskQueryService.DeviceOptions(CONTEXT, 101L)));
        verifyNoInteractions(shipments);
    }

    @BeforeEach
    void setIdentity() {
        TenantContextHolder.setTenantId(TENANT_ID);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(ACTOR_ID).setTenantId(TENANT_ID),
                new MockHttpServletRequest());
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void pageUsesOneBatchScopeForCountAndRowsAndResolvesOwnerAndCreatorSeparately() {
        stubQueryScope();
        var request = pageRequest();
        when(tasks.selectPageCount(any(InspectionTaskPageQuery.class))).thenAnswer(call -> {
            assertPageQuery(call.getArgument(0));
            return 1L;
        });
        when(tasks.selectPageList(any(InspectionTaskPageQuery.class))).thenAnswer(call -> {
            assertPageQuery(call.getArgument(0));
            return List.of(task(31L, "PREPARING"));
        });
        stubPeople();

        var page = service.page(request);

        assertEquals(1L, page.getTotal());
        assertEquals(1, page.getList().size());
        assertTaskPeople(page.getList().getFirst());
        verify(access).permission(argThat(InspectionTaskQueryServiceTest::isActor), eq(TASK_QUERY));
        verify(access).queryScope(argThat(InspectionTaskQueryServiceTest::isActor));
        verifyNoMoreInteractions(access);
        verify(tasks).selectPageCount(any(InspectionTaskPageQuery.class));
        verify(tasks).selectPageList(any(InspectionTaskPageQuery.class));
        verify(users).getUserMap(Set.of(OWNER_ID, CREATOR_ID));
        verifyNoMoreInteractions(tasks, users);
        verifyNoPerProjectChecks();
        verifyNoInteractions(projectContexts, issues);
    }

    @Test
    void emptyPageStillUsesTheBatchScopeButDoesNotFetchRowsOrPeople() {
        stubQueryScope();
        when(tasks.selectPageCount(any(InspectionTaskPageQuery.class))).thenAnswer(call -> {
            assertPageQuery(call.getArgument(0));
            return 0L;
        });

        var page = service.page(pageRequest());

        assertEquals(0L, page.getTotal());
        assertTrue(page.getList().isEmpty());
        verify(access).permission(argThat(InspectionTaskQueryServiceTest::isActor), eq(TASK_QUERY));
        verify(access).queryScope(argThat(InspectionTaskQueryServiceTest::isActor));
        verifyNoMoreInteractions(access);
        verify(tasks).selectPageCount(any(InspectionTaskPageQuery.class));
        verifyNoMoreInteractions(tasks);
        verifyNoInteractions(users, projectContexts, issues);
        verifyNoPerProjectChecks();
    }

    @Test
    void deniedTaskPagePermissionStopsBeforeResolvingScopeOrQueryingTasks() {
        doThrow(new InspectionException(InspectionException.Reason.FORBIDDEN, "没有查询权限"))
                .when(access).permission(argThat(InspectionTaskQueryServiceTest::isActor), eq(TASK_QUERY));

        var failure = assertThrows(InspectionException.class, () -> service.page(pageRequest()));

        assertEquals(InspectionException.Reason.FORBIDDEN, failure.getReason());
        verify(access).permission(argThat(InspectionTaskQueryServiceTest::isActor), eq(TASK_QUERY));
        verifyNoMoreInteractions(access);
        verifyNoInteractions(tasks, projectContexts, users, issues);
    }

    @Test
    void workbenchCountsTheFullScopeThenQueriesPendingAndCreatedSectionsAtInvocationTime() {
        when(permissions.hasAnyPermissions(ACTOR_ID, TASK_QUERY)).thenReturn(true);
        when(permissions.hasAnyPermissions(ACTOR_ID, ISSUE_QUERY)).thenReturn(true);
        stubQueryScope();
        var counts = List.of(new InspectionStateCount("PREPARING", 9), new InspectionStateCount("ARCHIVED", 4));
        when(tasks.selectStateCounts(any(InspectionTaskPageQuery.class))).thenAnswer(call -> {
            InspectionTaskPageQuery query = call.getArgument(0);
            assertWorkbenchScope(query);
            assertNull(query.getWorkbenchSection(), "Statistics must precede and remain independent of either table section");
            assertNull(query.getStatusCode());
            return counts;
        });
        var sectionsAtInvocation = new ArrayList<InspectionTaskPageQuery.WorkbenchSection>();
        when(tasks.selectPageList(any(InspectionTaskPageQuery.class))).thenAnswer(call -> {
            InspectionTaskPageQuery query = call.getArgument(0);
            assertWorkbenchScope(query);
            var section = query.getWorkbenchSection();
            sectionsAtInvocation.add(section);
            assertEquals(sectionsAtInvocation.size() == 1 ? InspectionTaskPageQuery.WorkbenchSection.PENDING
                    : InspectionTaskPageQuery.WorkbenchSection.CREATED, section);
            return List.of(task(section == InspectionTaskPageQuery.WorkbenchSection.PENDING ? 31L : 32L,
                    section == InspectionTaskPageQuery.WorkbenchSection.PENDING ? "PREPARING" : "EXECUTING"));
        });
        var issue = issue();
        when(issues.selectWorkbench(new ServiceIssueWorkbenchQuery(TENANT_ID, ACTOR_ID, VISIBLE, 20)))
                .thenReturn(List.of(issue));
        stubPeople();

        var result = service.workbench(CONTEXT);

        assertEquals(counts, result.taskCounts());
        assertEquals(List.of(InspectionTaskPageQuery.WorkbenchSection.PENDING,
                InspectionTaskPageQuery.WorkbenchSection.CREATED), sectionsAtInvocation);
        assertEquals(31L, result.pendingTasks().getFirst().getId());
        assertEquals(32L, result.createdTasks().getFirst().getId());
        assertTaskPeople(result.pendingTasks().getFirst());
        assertTaskPeople(result.createdTasks().getFirst());
        assertEquals(61L, result.myIssues().getFirst().getId());
        verify(access).queryScope(CONTEXT);
        verify(access, never()).visibleProjects(any());
        verifyNoMoreInteractions(access);
        verify(tasks).selectStateCounts(any(InspectionTaskPageQuery.class));
        verify(tasks, times(2)).selectPageList(any(InspectionTaskPageQuery.class));
        verifyNoMoreInteractions(tasks);
        verify(users, times(2)).getUserMap(Set.of(OWNER_ID, CREATOR_ID));
        verify(issues).selectWorkbench(new ServiceIssueWorkbenchQuery(TENANT_ID, ACTOR_ID, VISIBLE, 20));
        verifyNoMoreInteractions(issues);
        verifyNoInteractions(projectContexts);
        verifyNoPerProjectChecks();
    }

    @Test
    void issueOnlyWorkbenchDoesNotQueryTaskCountsListsOrManagerMembership() {
        when(permissions.hasAnyPermissions(ACTOR_ID, TASK_QUERY)).thenReturn(false);
        when(permissions.hasAnyPermissions(ACTOR_ID, ISSUE_QUERY)).thenReturn(true);
        when(access.visibleProjects(CONTEXT)).thenReturn(VISIBLE);
        when(issues.selectWorkbench(new ServiceIssueWorkbenchQuery(TENANT_ID, ACTOR_ID, VISIBLE, 20)))
                .thenReturn(List.of(issue()));

        var result = service.workbench(CONTEXT);

        assertTrue(result.taskCounts().isEmpty());
        assertTrue(result.pendingTasks().isEmpty());
        assertTrue(result.createdTasks().isEmpty());
        assertEquals(61L, result.myIssues().getFirst().getId());
        assertEquals("XJ-31", result.myIssues().getFirst().getTaskCode());
        verify(access).visibleProjects(CONTEXT);
        verifyNoMoreInteractions(access);
        verifyNoInteractions(tasks, projectContexts, users);
        verify(issues).selectWorkbench(new ServiceIssueWorkbenchQuery(TENANT_ID, ACTOR_ID, VISIBLE, 20));
        verifyNoPerProjectChecks();
    }

    @Test
    void workbenchWithoutEitherQueryPermissionDoesNotResolveOrQueryAnything() {
        var failure = assertThrows(InspectionException.class, () -> service.workbench(CONTEXT));

        assertEquals(InspectionException.Reason.FORBIDDEN, failure.getReason());
        verifyNoInteractions(access, tasks, issues, users, projectContexts);
    }

    @Test
    void projectOptionsDelegateOnceToTheOwnerWithTheSameTenantActorAndKeyword() {
        var keyword = "  项目%_  ";
        var expected = new ProjectBusinessContextApi.OptionsQuery(TENANT_ID, ACTOR_ID, keyword);
        when(projectContexts.searchOptions(any(ProjectBusinessContextApi.OptionsQuery.class))).thenAnswer(call -> {
            assertEquals(expected, call.getArgument(0));
            return List.of(new ProjectBusinessContextApi.Option(101L, "P101", "项目101", "客户101"));
        });

        assertEquals(List.of(new InspectionTaskQueryService.ProjectOption(101L, "P101", "项目101", "客户101")),
                service.projects(new InspectionTaskQueryService.ProjectOptions(CONTEXT, keyword)));

        verify(access).permission(CONTEXT, TASK_QUERY);
        verifyNoMoreInteractions(access);
        verify(projectContexts).searchOptions(expected);
        verifyNoMoreInteractions(projectContexts);
        verifyNoInteractions(tasks, users);
        verifyNoPerProjectChecks();
    }

    private void stubQueryScope() {
        when(access.queryScope(any(InspectionRequestContext.class))).thenAnswer(call -> {
            assertTrue(isActor(call.getArgument(0)), "Scope must belong to the authenticated tenant and actor");
            return SCOPE;
        });
    }

    private void stubPeople() {
        var owner = new AdminUserRespDTO(); owner.setId(OWNER_ID); owner.setNickname("任务负责工程师");
        var creator = new AdminUserRespDTO(); creator.setId(CREATOR_ID); creator.setNickname("任务创建工程师");
        when(users.getUserMap(Set.of(OWNER_ID, CREATOR_ID))).thenReturn(Map.of(OWNER_ID, owner, CREATOR_ID, creator));
    }

    private void verifyNoPerProjectChecks() {
        verify(access, never()).manager(any());
        verify(access, never()).project(any());
        verify(access, never()).task(any());
    }

    private static boolean isActor(InspectionRequestContext context) {
        return context != null && TENANT_ID.equals(context.tenantId()) && ACTOR_ID.equals(context.actorId());
    }

    private static InspectionTaskQueryService.PageQuery pageRequest() {
        var query = new InspectionTaskQueryService.PageQuery();
        query.setPageNo(2); query.setPageSize(10); query.setKeyword("季度巡检");
        query.setProjectId(103L); query.setMode("OFFLINE"); query.setStatusCode("PREPARING");
        return query;
    }

    private static void assertPageQuery(InspectionTaskPageQuery query) {
        assertScope(query);
        assertEquals(2, query.getPageNo()); assertEquals(10, query.getPageSize()); assertEquals(10, query.getOffset());
        assertEquals("季度巡检", query.getKeyword()); assertEquals(103L, query.getProjectId());
        assertEquals("OFFLINE", query.getMode()); assertEquals("PREPARING", query.getStatusCode());
        assertNull(query.getWorkbenchSection());
    }

    private static void assertWorkbenchScope(InspectionTaskPageQuery query) {
        assertScope(query);
        assertEquals(1, query.getPageNo()); assertEquals(8, query.getPageSize()); assertEquals(0, query.getOffset());
    }

    private static void assertScope(InspectionTaskPageQuery query) {
        assertEquals(TENANT_ID, query.getTenantId()); assertEquals(ACTOR_ID, query.getActorId());
        assertEquals(VISIBLE, query.getVisibleProjectIds()); assertEquals(MANAGED, query.getManagedProjectIds());
    }

    private static void assertTaskPeople(InspectionViews.TaskSummary task) {
        assertEquals(OWNER_ID, task.getOwnerUserId());
        assertEquals("任务负责工程师", task.getOwnerName());
        assertEquals("任务创建工程师", task.getCreatorName());
        assertNotEquals(task.getOwnerName(), task.getCreatorName());
    }

    private static InspectionTaskDO task(Long id, String status) {
        var task = new InspectionTaskDO();
        task.setId(id); task.setTenantId(TENANT_ID); task.setProjectId(101L); task.setVersion(5L);
        task.setTaskCode("XJ-" + id); task.setTaskName("任务" + id); task.setStatusCode(status);
        task.setOwnerUserId(OWNER_ID); task.setCreator(CREATOR_ID.toString());
        return task;
    }

    private static ServiceIssueSummary issue() {
        var issue = new ServiceIssueSummary();
        issue.setId(61L); issue.setTenantId(TENANT_ID); issue.setTaskId(31L);
        issue.setTaskCode("XJ-31"); issue.setTaskName("待办巡检");
        issue.setResponsibleUserId(ACTOR_ID); issue.setStatusCode("PENDING"); issue.setDescription("待核实问题");
        return issue;
    }
}
