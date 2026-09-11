package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectInstantiation;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeResolutionService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectTemplateMatchHistoryService;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.InitialMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.PreparationInitializationApi;
import cn.iocoder.yudao.module.pms.engineering.api.preparation.dto.PreparationInitializationCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.company.dto.CompanyRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProjectManualCreationApplicationServiceTest {

    @Mock
    private PlatformCommandExecutionApi platformFactService;
    @Mock
    private ProjectManualCreationService projectCreationService;
    @Mock
    private ProjectCreationAuthorizationService authorizationService;
    @Mock
    private ProjectServiceManagerCandidateValidator managerCandidateValidator;
    @Mock
    private CompanyApi companyApi;
    @Mock
    private DeptApi deptApi;
    @Mock
    private OrganizationScopeApi organizationScopeApi;
    @Mock
    private ProjectSiteApplicationService projectSiteService;
    @Mock
    private ProjectAttributeResolutionService projectAttributeResolutionService;
    @Mock
    private ProjectTemplateMatchHistoryService templateMatchHistoryService;
    @Mock
    private ProjectTreeProjectionService projectTreeProjectionService;
    @Mock
    private ProjectTemplateService projectTemplateService;
    @Mock
    private ProjectWorkBindingFactApi projectWorkBindingFactApi;
    @Mock
    private PreparationInitializationApi preparationInitializationApi;
    @Mock
    private CustomerQueryApi customerQueryApi;

    @InjectMocks
    private ProjectManualCreationApplicationService service;

    @Test
    @SuppressWarnings("unchecked")
    void selectedCustomerCreationUsesOwnerIdentityInsideTheOriginalCreationTransaction() {
        var command = command();
        command.draft().setCustomerCode("C-001");
        command.draft().setCustomerName("客户端名称不可作为主档事实");
        var source = new CustomerSummaryDTO(55L, 1L, "C-001", "主档客户", null,
                "ENABLED", "PLATFORM_TEMPORARY", 3L, null);
        when(customerQueryApi.getCustomerByCode(new CustomerCodeQuery("C-001", 7L))).thenReturn(source);
        var decision = decision();
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any())).thenReturn(decision);
        when(projectCreationService.createProject(any(), any(), any(), eq(decision), isNull())).thenReturn(project());
        when(projectCreationService.getInstancesForCreation(100L, 1L)).thenReturn(new ProjectInstantiation());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            assertEquals(ProjectManualCreationApplicationService.SELECTED_CUSTOMER_CREATE_SCOPE,
                    ((PlatformCommandExecutionApi.IdempotencyScope) invocation.getArgument(0)).scopeCode());
            Object result = ((Supplier<?>) invocation.getArgument(3)).get();
            ((Function<Object, ?>) invocation.getArgument(4)).apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
        assertEquals(100L, service.createWithSelectedCustomer(command, actor()).id());
        var draft = ArgumentCaptor.forClass(ProjectMasterDO.class);
        verify(projectCreationService).createProject(draft.capture(), any(), any(), eq(decision), isNull());
        assertEquals(55L, draft.getValue().getCustomerId());
        assertEquals("C-001", draft.getValue().getCustomerCode());
        assertEquals("主档客户", draft.getValue().getCustomerName());
        verify(templateMatchHistoryService).appendInitial(any());
    }

    @Test
    void selectedCustomerRejectsUnavailableOrForeignTenantBeforeBusinessWrites() {
        var command = command();
        command.draft().setCustomerCode("C-001");
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(3)).get());
        var query = new CustomerCodeQuery("C-001", 7L);
        for (var customer : java.util.Arrays.asList(null,
                new CustomerSummaryDTO(55L, 1L, "C-001", "客户", null, "DISABLED", "PLATFORM_CREATED", 1L, null),
                new CustomerSummaryDTO(55L, 2L, "C-001", "客户", null, "ENABLED", "PLATFORM_CREATED", 1L, null))) {
            when(customerQueryApi.getCustomerByCode(query)).thenReturn(customer);
            var failure = assertThrows(ServiceException.class, () -> service.createWithSelectedCustomer(command, actor()));
            assertEquals(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_CUSTOMER_UNAVAILABLE.getCode(), failure.getCode());
        }
        verifyNoInteractions(projectCreationService, projectAttributeResolutionService,
                templateMatchHistoryService, projectTreeProjectionService);
        verify(companyApi, org.mockito.Mockito.times(3)).validateCompanyList(java.util.List.of(10L));
        verify(deptApi, org.mockito.Mockito.times(3)).validateDeptList(java.util.List.of(20L));
        verify(companyApi, org.mockito.Mockito.never()).getCompany(any());
        verify(deptApi, org.mockito.Mockito.never()).getDept(any());
    }

    @Test
    void selectedCustomerReplayDoesNotQueryCustomerOrCreateAgain() {
        var command = command();
        command.draft().setCustomerCode("C-001");
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, null));
        service.createWithSelectedCustomer(command, actor());
        verifyNoInteractions(customerQueryApi, projectCreationService);
        verify(companyApi).validateCompanyList(java.util.List.of(10L));
        verify(deptApi).validateDeptList(java.util.List.of(20L));
    }

    @BeforeEach
    void setUpOrganization() {
        CompanyRespDTO company = new CompanyRespDTO();
        company.setId(10L); company.setCode("CO-01"); company.setName("公司一");
        DeptRespDTO department = new DeptRespDTO();
        department.setId(20L); department.setCode("DEP-01"); department.setName("办事处一");
        lenient().when(companyApi.getCompany(10L)).thenReturn(company);
        lenient().when(deptApi.getDept(20L)).thenReturn(department);
        lenient().when(organizationScopeApi.hasScope(7L, 10L, 20L)).thenReturn(true);
        lenient().when(projectSiteService.validateLocationScope(any(), any())).thenReturn("UNRESOLVED");
        lenient().when(projectTemplateService.getExecutionSnapshot(any(), any()))
                .thenReturn(new TemplateExecutionSnapshot());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationEntryBuildsResultInsidePlatformExecution() {
        ProjectMasterDO project = project();
        TemplateMatchDecision matchDecision = decision();
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any())).thenReturn(matchDecision);
        when(projectCreationService.createProject(any(), any(), any(), eq(matchDecision), isNull())).thenReturn(project);
        when(projectCreationService.getInstancesForCreation(100L, 1L)).thenReturn(new ProjectInstantiation());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, ?> facts = invocation.getArgument(4);
            Object result = operation.get();
            facts.apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });

        var result = service.create(command(), actor());

        assertEquals(100L, result.id());
        assertEquals("ACTIVE", result.lifecycleStatus());
        assertEquals("S0", result.currentStage());
        assertEquals("UNASSIGNED", result.assignmentStatus());
        assertEquals(false, result.serviceManagerAssigned());
        assertEquals(TemplateMatchDecisionRules.MATCH_UNIQUE, result.matchResult());
        assertEquals(TemplateMatchDecisionRules.DECISION_EXPLICIT, result.matchDecisionMode());
        String expectedOperationId = TemplateMatchDecisionRules.operationId(
                100L, TemplateMatchDecisionRules.TRIGGER_INITIAL, "key-1");
        assertEquals(expectedOperationId, result.matchOperationId());
        verify(authorizationService).assertCanCreate(7L);
        ArgumentCaptor<ProjectMasterDO> draftCaptor = ArgumentCaptor.forClass(ProjectMasterDO.class);
        verify(projectCreationService).createProject(draftCaptor.capture(), any(), any(), eq(matchDecision), isNull());
        assertEquals(1L, draftCaptor.getValue().getTenantId());
        ArgumentCaptor<InitialMatchHistoryCommand> historyCaptor =
                ArgumentCaptor.forClass(InitialMatchHistoryCommand.class);
        verify(templateMatchHistoryService).appendInitial(historyCaptor.capture());
        verify(projectTreeProjectionService).publish(100L, 1L, "PROJECT_CREATE:100");
        assertEquals(100L, historyCaptor.getValue().projectId());
        assertEquals(7L, historyCaptor.getValue().operatorId());
        assertEquals("业务立项", historyCaptor.getValue().changeReason());
        assertEquals(expectedOperationId, historyCaptor.getValue().operationId());
        assertEquals("correlation-1", historyCaptor.getValue().traceId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsConfirmedServiceManagerWithoutClaimingBothPrimaryRolesAreAssigned() {
        var base = command();
        var command = new ManualProjectCreateCommand(base.draft(), 10L, 20L, base.sites(),
                base.templateRevisionId(), base.candidateWatermark(), 8L, base.idempotencyKey(), base.requestDigest());
        var decision = decision();
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any())).thenReturn(decision);
        when(projectCreationService.createProject(any(), any(), any(), eq(decision), eq(8L))).thenReturn(project());
        when(projectCreationService.getInstancesForCreation(100L, 1L)).thenReturn(new ProjectInstantiation());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<Object>) invocation.getArgument(3)).get()));
        var result = service.create(command, actor());
        assertEquals(true, result.serviceManagerAssigned());
        assertEquals("UNASSIGNED", result.assignmentStatus());
        verify(authorizationService).assertCanAssign(7L);
        verify(managerCandidateValidator).validate(8L, 10L, 20L, "DEP-01");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preparationBindingInitializesInsideProjectCreationOperation() {
        ProjectMasterDO project = project();
        TemplateMatchDecision matchDecision = decision();
        TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
        TemplateExecutionSnapshot.TaskContract task = new TemplateExecutionSnapshot.TaskContract();
        task.setNodeKey("task:PRE-02");
        TemplateExecutionSnapshot.BindingContract binding = new TemplateExecutionSnapshot.BindingContract();
        binding.setType("BUSINESS_OBJECT");
        binding.setTargetContextCode("SOL");
        binding.setTargetObjectType("SITE_SURVEY_PREPARATION");
        binding.setTargetObjectKey("PRE_02_SITE_SURVEY");
        task.setBinding(binding);
        snapshot.getTasks().add(task);
        when(projectTemplateService.getExecutionSnapshot(9L, 2)).thenReturn(snapshot);
        when(projectWorkBindingFactApi.inspect(any())).thenReturn(new ProjectWorkBindingFact(
                100L, 0, 200L, 3, 300L, 4, 400L, 5,
                "BUSINESS_OBJECT", "SOL", "SITE_SURVEY_PREPARATION", "PRE_02_SITE_SURVEY",
                "PRE_02_SITE_SURVEY", 1, 1, "[]"));
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any())).thenReturn(matchDecision);
        when(projectCreationService.createProject(any(), any(), any(), eq(matchDecision), isNull())).thenReturn(project);
        when(projectCreationService.getInstancesForCreation(100L, 1L)).thenReturn(new ProjectInstantiation());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Object result = operation.get();
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });

        service.create(command(), actor());

        ArgumentCaptor<PreparationInitializationCommand> captor =
                ArgumentCaptor.forClass(PreparationInitializationCommand.class);
        verify(preparationInitializationApi).initialize(captor.capture());
        assertEquals("PRE02_INIT:100:300:4", captor.getValue().idempotencyKey());
        assertEquals("PROJECT_CREATE:100:PRE02", captor.getValue().operationId());
        assertEquals(PreparationInitializationApi.TRIGGER_PROJECT_CREATION,
                captor.getValue().triggerType());
        assertEquals(7L, captor.getValue().actorUserId());
    }

    @Test
    void conflictDecisionIsMappedToStableError() {
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(command(), actor()));

        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), exception.getCode());
    }

    @Test
    void rootCreationRequiresCandidateWatermark() {
        ManualProjectCreateCommand base = command();
        ManualProjectCreateCommand invalid = new ManualProjectCreateCommand(
                base.draft(), 10L, 20L, java.util.List.of(), base.templateRevisionId(),
                null, null, base.idempotencyKey(), base.requestDigest());

        assertThrows(IllegalArgumentException.class, () -> service.create(invalid, actor()));

        verifyNoInteractions(platformFactService);
        verifyNoInteractions(authorizationService);
    }

    @Test
    void authorizationFailureStopsBeforeIdempotencyClaim() {
        doThrow(new ServiceException(FORBIDDEN))
                .when(authorizationService).assertCanCreate(7L);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create(command(), actor()));

        assertEquals(FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(platformFactService, projectCreationService);
    }

    @Test
    void rootCreationRejectsMajorProjectLevelBeforePlatformExecution() {
        ManualProjectCreateCommand invalid = command();
        invalid.draft().setMajorProjectLevel("MAJOR");

        assertThrows(IllegalArgumentException.class, () -> service.create(invalid, actor()));

        verifyNoInteractions(platformFactService, authorizationService, projectCreationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void historyFailureEscapesPlatformTransaction() {
        ProjectMasterDO project = project();
        TemplateMatchDecision matchDecision = decision();
        when(projectAttributeResolutionService.resolveInitial(any(), any(), any())).thenReturn(matchDecision);
        when(projectCreationService.createProject(any(), any(), any(), eq(matchDecision), isNull())).thenReturn(project);
        when(projectCreationService.getInstancesForCreation(100L, 1L)).thenReturn(new ProjectInstantiation());
        doThrow(new IllegalStateException("history insert failed"))
                .when(templateMatchHistoryService).appendInitial(any());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            return operation.get();
        });

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.create(command(), actor()));

        assertEquals("history insert failed", exception.getMessage());
        verify(projectSiteService).bindSites(eq(100L), any());
    }

    @Test
    void childCreationMayInheritTemplateWithoutCandidateWatermark() {
        ManualProjectCreateCommand base = command();
        base.draft().setParentId(100L);
        ManualProjectCreateCommand child = new ManualProjectCreateCommand(base.draft(), 10L, 20L,
                java.util.List.of(), null, null, null, base.idempotencyKey(), base.requestDigest());
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.IN_PROGRESS, null));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.create(child, actor()));

        assertEquals(PMS_IDEMPOTENCY_IN_PROGRESS.getCode(), exception.getCode());
    }

    private ManualProjectCreateCommand command() {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setCreationReason("业务立项");
        draft.setImplementationLocation("上海");
        draft.setSigningMethod("DIRECT");
        draft.setProjectCategory("GENERAL");
        draft.setImplementationMode("DIRECT_SERVICE");
        return new ManualProjectCreateCommand(draft, 10L, 20L, java.util.List.of(), 9002L, "candidate-watermark-v1",
                null, "key-1", "a".repeat(64));
    }

    private TemplateMatchDecision decision() {
        return new TemplateMatchDecision(TemplateMatchDecisionRules.MATCH_UNIQUE, "candidate-watermark-v1",
                TemplateMatchDecisionRules.MATCHER_VERSION, TemplateMatchDecisionRules.DECISION_EXPLICIT,
                9L, 9002L, 2);
    }

    private ProjectManualCreationApplicationService.Actor actor() {
        return new ProjectManualCreationApplicationService.Actor(1L, 7L, "correlation-1");
    }

    private ProjectMasterDO project() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setProjectCode("PJT2026000100");
        project.setStatus("S0");
        project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S0");
        project.setAssignmentStatus("UNASSIGNED");
        project.setCreationReason("业务立项");
        project.setVersion(0);
        project.setLifecycleTemplateId(9L);
        project.setLifecycleTemplateRevisionNo(2);
        return project;
    }
}
