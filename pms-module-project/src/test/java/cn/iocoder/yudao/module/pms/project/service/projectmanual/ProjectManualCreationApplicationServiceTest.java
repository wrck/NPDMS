package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;

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
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
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

    @InjectMocks
    private ProjectManualCreationApplicationService service;

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
        lenient().when(projectTemplateService.getRevisionContent(any(), any()))
                .thenReturn(new TemplateDefinitionContent());
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
    void preparationBindingInitializesInsideProjectCreationOperation() {
        ProjectMasterDO project = project();
        TemplateMatchDecision matchDecision = decision();
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        TemplateDefinitionContent.TaskDef task = new TemplateDefinitionContent.TaskDef();
        task.setWorkBindingTypeCode("BUSINESS_OBJECT");
        task.setTargetContextCode("SOL");
        task.setTargetObjectType("SITE_SURVEY_PREPARATION");
        task.setTargetObjectKey("PRE_02_SITE_SURVEY");
        content.setTasks(java.util.List.of(task));
        when(projectTemplateService.getRevisionContent(9L, 2)).thenReturn(content);
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
                null, base.idempotencyKey(), base.requestDigest());

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
                java.util.List.of(), null, null, base.idempotencyKey(), base.requestDigest());
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
                "key-1", "a".repeat(64));
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
