package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessExecutionServiceTest {
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper nodes = mock(ProjectNodeExecutionMapper.class);
    final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    final TaskBusinessAccess access = mock(TaskBusinessAccess.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final BusinessViewQueryApi views = mock(BusinessViewQueryApi.class);
    final ProjectBusinessExecutionService service = new ProjectBusinessExecutionService(projects, plans, nodes, executions, access, scopes, permissions,
            new org.springframework.beans.factory.ObjectProvider<BusinessViewQueryApi>() {
                @Override public BusinessViewQueryApi getObject() { return views; }
            });
    final ProjectMasterDO project = new ProjectMasterDO();
    final ProjectPlanVersionDO plan = new ProjectPlanVersionDO();
    final TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
    final ProjectNodeExecutionDO node = new ProjectNodeExecutionDO();
    final ProjectTaskExecutionContext task = new ProjectTaskExecutionContext(9L, 1, 11L, 1, 12L, 1, 21L, 31L, 1, 2, 32L, 1, true, null);
    final ProjectStageExecutionContext stage = new ProjectStageExecutionContext(9L, 1, 11L, 1, 12L, 1, 21L, 31L, 1, 2, true);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        var login = new LoginUser(); login.setId(8L); login.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(login, new MockHttpServletRequest());
        project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(21L);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        plan.setId(21L); when(plans.selectEffective(any())).thenReturn(plan);
        node.setId(31L); node.setNodeInstanceId(11L); node.setContractId(12L); node.setPlanVersionId(21L);
        node.setNodeKind("TASK"); node.setNodeKey("task:survey"); node.setStatus("ACTIVE");
        var definition = new TemplateExecutionSnapshot.TaskContract(); definition.setNodeKey(node.getNodeKey()); definition.setBinding(binding());
        snapshot.setTasks(List.of(definition)); freeze();
        when(nodes.selectCurrent(any())).thenReturn(List.of(node));
        when(executions.inspect(any())).thenReturn(task);
        when(executions.inspectStage(any())).thenReturn(stage);
        var row = new ProjectTaskInstanceDO(); row.setId(11L);
        when(access.read(11L,7L,8L)).thenReturn(row); when(access.writable(eq(row),eq(project),any())).thenReturn(true);
        when(views.getRevision(any())).thenReturn(view("PUBLISHED"));
        when(permissions.hasAnyPermissions(8L,"pms:project-task:execute")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void ownerViewCanDependOnWriteGuardWithoutAnInitializationCycle() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            var beans = context.getBeanFactory();
            beans.registerSingleton("projects", projects);
            beans.registerSingleton("plans", plans);
            beans.registerSingleton("nodes", nodes);
            beans.registerSingleton("executions", executions);
            beans.registerSingleton("access", access);
            beans.registerSingleton("scopes", scopes);
            beans.registerSingleton("permissions", permissions);
            context.registerBean(ProjectBusinessExecutionService.class);
            context.registerBean(BusinessViewQueryApi.class, () -> {
                assertNotNull(context.getBean(ProjectBusinessExecutionService.class));
                return views;
            });
            context.refresh();
            context.getBean(ProjectBusinessExecutionService.class).lockForWrite(request(null));
            verify(views).getRevision(new BusinessViewQueryApi.Query(40L, BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
            verify(executions).lockAndRevalidate(task);
        }
    }
    void freeze() { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); }
    ProjectBusinessExecutionApi.WriteRequest request(ProjectBusinessExecutionSelection selected) {
        return new ProjectBusinessExecutionApi.WriteRequest(9L,"SOL","SITE_SURVEY",selected);
    }
    BusinessViewRevision view(String status) {
        return new BusinessViewRevision(40L,"SITE_SURVEY","survey",1L,"SOL",BusinessViewComponentProvider.ViewSource.PAGE,
                "SOL_SITE_SURVEY","1",null,JsonUtils.parseTree("{}"),JsonUtils.parseTree("[]"),"query","command","permission",null,null,1,status,Set.of());
    }
    TemplateExecutionSnapshot.BindingContract binding() {
        var b = new TemplateExecutionSnapshot.BindingContract(); b.setType("BUSINESS_COMPONENT"); b.setTargetContextCode("SOL");
        b.setTargetObjectType("SITE_SURVEY"); b.setComponentKey("SOL_SITE_SURVEY");
        b.setParameters(JsonUtils.parseTree("{\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}"));
        b.setBusinessViewSnapshot(JsonUtils.parseTree(JsonUtils.toJsonString(view("PUBLISHED")))); return b;
    }
    void useStage() {
        node.setNodeKind("STAGE"); node.setNodeKey("stage:survey");
        var d = new TemplateExecutionSnapshot.StageContract(); d.setNodeKey(node.getNodeKey()); d.setBinding(binding());
        snapshot.setStages(List.of(d)); snapshot.setTasks(List.of()); freeze();
    }
    @Test void standaloneUsesUniqueCurrentTaskAndOriginalTaskAuthorizationUnderProjectLock() {
        service.lockForWrite(request(null));
        var order = inOrder(projects, executions);
        order.verify(projects).selectByIdForUpdate(9L);
        order.verify(executions, times(2)).inspect(new ProjectTaskExecutionQuery(9L,11L,12L));
        order.verify(executions).lockAndRevalidate(task);
        verify(access).read(11L,7L,8L);
    }
    @Test void explicitStaleContextNeverFallsBackToCurrentRound() {
        var stale = new ProjectTaskExecutionContext(9L,1,11L,0,12L,1,21L,31L,0,2,32L,1,true,null);
        when(executions.lockAndRevalidate(stale)).thenThrow(new IllegalStateException("stale"));
        assertThrows(IllegalStateException.class, () -> service.lockForWrite(request(new ProjectBusinessExecutionSelection(stale,null))));
        verify(executions,never()).inspect(any()); verify(executions).lockAndRevalidate(stale);
    }
    @Test void stageUsesStageContextAndMarksHandlingWithoutTaskIdentity() {
        useStage(); service.lockForWrite(request(new ProjectBusinessExecutionSelection(null,stage)));
        verify(executions).beginStageHandling(stage,8L); verify(executions,never()).lockAndRevalidate(any()); verifyNoInteractions(access);
    }

    @ParameterizedTest @ValueSource(strings={"TASK","STAGE"})
    void requirementAnalysisReusesTheGuardWithItsOwnFrozenBusinessObjectView(String kind) {
        if ("STAGE".equals(kind)) useStage();
        var definition = "TASK".equals(kind) ? snapshot.getTasks().getFirst().getBinding()
                : snapshot.getStages().getFirst().getBinding();
        definition.setType("BUSINESS_OBJECT");
        definition.setTargetObjectType("REQUIREMENT_ANALYSIS");
        definition.setTargetObjectKey("PRE_04_REQUIREMENT_ANALYSIS");
        definition.setComponentKey("PROJ_REQUIREMENT_ANALYSIS");
        definition.setParameters(JsonUtils.parseTree("""
                {"instanceResolutionStrategy":"CREATE_ON_FIRST_ACTION","schemaVersion":2,
                 "dynamicFormTemplateId":800,"dynamicFormTemplateRevisionId":801}
                """));
        var registered = new BusinessViewRevision(40L,"REQUIREMENT_ANALYSIS","requirement",1L,"SOL",
                BusinessViewComponentProvider.ViewSource.PAGE,"PROJ_REQUIREMENT_ANALYSIS","1",801L,
                JsonUtils.parseTree("{}"),JsonUtils.parseTree("[]"),"query","command","permission",null,null,1,"PUBLISHED",Set.of());
        definition.setBusinessViewSnapshot(JsonUtils.parseTree(JsonUtils.toJsonString(registered)));
        freeze();
        when(views.getRevision(any())).thenReturn(registered);
        var selected = "TASK".equals(kind) ? new ProjectBusinessExecutionSelection(task,null)
                : new ProjectBusinessExecutionSelection(null,stage);
        var requested = new ProjectBusinessExecutionApi.WriteRequest(9L,"SOL","REQUIREMENT_ANALYSIS",selected);

        service.lockForWrite(requested);

        if ("TASK".equals(kind)) verify(executions).lockAndRevalidate(task);
        else verify(executions).beginStageHandling(stage,8L);
        var disabled = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(registered));
        disabled.put("status","DISABLED");
        when(views.getRevision(any())).thenReturn(JsonUtils.convertObject(disabled,BusinessViewRevision.class));
        clearInvocations(executions);
        assertThrows(ServiceException.class, () -> service.lockForWrite(requested));
        verifyNoInteractions(executions);
    }
    @Test void multipleCurrentCandidatesRequireExplicitSelection() {
        var other = new ProjectNodeExecutionDO(); other.setId(99L); other.setNodeKind("TASK"); other.setNodeKey(node.getNodeKey());
        other.setNodeInstanceId(13L); other.setContractId(14L); other.setPlanVersionId(21L); other.setStatus("ACTIVE");
        when(nodes.selectCurrent(any())).thenReturn(List.of(node,other));
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        verify(executions,never()).lockAndRevalidate(any());
        service.lockForWrite(request(new ProjectBusinessExecutionSelection(task,null)));
        verify(executions).lockAndRevalidate(task);
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","DONE","CLOSED"})
    void inactiveRoundsCannotBeBypassedByMenuOrSuppliedContext(String status) {
        node.setStatus(status);
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(new ProjectBusinessExecutionSelection(task,null))));
        verifyNoInteractions(executions);
    }
    @Test void wrongOwnerTargetPlanAndMissingNodeDoNotPermitWrite() {
        assertThrows(ServiceException.class, () -> service.lockForWrite(new ProjectBusinessExecutionApi.WriteRequest(9L,"ACC","SITE_SURVEY",null)));
        node.setPlanVersionId(20L); assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        when(nodes.selectCurrent(any())).thenReturn(List.of()); assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        verifyNoInteractions(executions);
    }
    @Test void readOnlyBindingAndDisabledViewDenyWrite() {
        snapshot.getTasks().getFirst().getBinding().setParameters(JsonUtils.parseTree("{\"instanceResolutionStrategy\":\"READ_ONLY_AGGREGATE\"}")); freeze();
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        snapshot.getTasks().getFirst().setBinding(binding()); freeze(); when(views.getRevision(any())).thenReturn(view("DISABLED"));
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        verify(executions,never()).lockAndRevalidate(any());
    }
    @Test void taskAndStagePermissionsRemainEnforced() {
        when(access.writable(any(),any(),any())).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        useStage(); when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        verify(executions,never()).lockAndRevalidate(any()); verify(executions,never()).beginStageHandling(any(),any());
    }
    @Test void unmanagedProjectDoesNotRequireAPlanButCannotUseFabricatedNodeSelection() {
        project.setActivePlanVersionId(null);
        service.lockForWrite(request(null)); verifyNoInteractions(plans,nodes,executions);
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(new ProjectBusinessExecutionSelection(task,null))));
    }
    @Test void missingManagedPlanForeignTenantAndMixedSelectionAreRejected() {
        when(plans.selectEffective(any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        project.setTenantId(99L); assertThrows(ServiceException.class, () -> service.lockForWrite(request(null)));
        assertThrows(ServiceException.class, () -> service.lockForWrite(request(new ProjectBusinessExecutionSelection(task,stage))));
        verifyNoInteractions(executions);
    }
}
