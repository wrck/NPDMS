package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectStageBusinessQueryServiceTest {
    private final ProjectManualCreationService projects = mock(ProjectManualCreationService.class);
    private final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    private final BusinessViewQueryApi views = mock(BusinessViewQueryApi.class);
    private final StageBusinessViewProvider owner = mock(StageBusinessViewProvider.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    private final ProjectStageExecutionContext execution = new ProjectStageExecutionContext(9L, 1, 90L, 1, 99L, 1, 100L, 101L, 1, 2, true);
    private final ProjectStageApprovalService approvals = mock(ProjectStageApprovalService.class);
    private final ProjectStageBusinessQueryService service = new ProjectStageBusinessQueryService(projects, graph, views, List.of(owner), executions, approvals);
    private final ProjectAccessActor actor = new ProjectAccessActor(1L, 7L);
    private ProjectStageInstanceDO stage;
    private ProjectStageExecutionContractDO contract;

    @BeforeEach void setup() {
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        when(projects.getProject(9L, actor)).thenReturn(project);
        stage = new ProjectStageInstanceDO(); stage.setId(90L); stage.setProjectId(9L); stage.setTenantId(1L);
        stage.setStageCode("S4"); stage.setGraphVersion(1L); stage.setStatus("ACTIVE");
        contract = new ProjectStageExecutionContractDO(); contract.setId(99L); contract.setProjectId(9L); contract.setStageId(90L);
        contract.setTenantId(1L); contract.setGraphVersion(1L); contract.setSourceNodeKey("stage-4");
        contract.setBindingVersion(1);
        binding("STAGE_NATIVE");
        when(graph.selectStages(any())).thenReturn(List.of(stage)); when(graph.selectContracts(any())).thenReturn(List.of(contract));
        when(owner.ownerContext()).thenReturn("SOL"); when(owner.objectType()).thenReturn("REQUIREMENT_ANALYSIS");
        when(owner.inspectStage(any())).thenReturn(new StageBusinessViewProvider.Result(Set.of("QUERY", "PATCH_FORM")));
        when(views.getRevision(any())).thenReturn(view("PUBLISHED"));
        when(executions.inspectStage(any())).thenReturn(execution);
    }
    private void binding(String type) {
        contract.setBindingType(type);
        var binding = new TemplateExecutionSnapshot.BindingContract();
        binding.setType(type);
        binding.setTargetContextCode("SOL"); binding.setTargetObjectType("REQUIREMENT_ANALYSIS");
        binding.setTargetObjectKey("PRE_04_REQUIREMENT_ANALYSIS"); binding.setComponentKey("PROJ_REQUIREMENT_ANALYSIS");
        binding.setParameters(JsonUtils.parseTree("{\"instanceResolutionStrategy\":\"CREATE_ON_FIRST_ACTION\"}"));
        binding.setBusinessViewSnapshot(JsonUtils.parseTree(JsonUtils.toJsonString(view("PUBLISHED"))));
        var permission = new TemplateExecutionSnapshot.PermissionContract();
        permission.setPolicySnapshot(JsonUtils.parseTree("{\"requiredActions\":[\"QUERY\"]}"));
        var frozen = new TemplateExecutionSnapshot.StageContract();
        frozen.setNodeKey("stage-4"); frozen.setCode("S4"); frozen.setBinding(binding); frozen.setPermission(permission);
        frozen.setCompletionRule(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));
        var snapshot = new TemplateExecutionSnapshot(); snapshot.setStages(List.of(frozen));
        contract.setBindingSnapshot(JsonUtils.toJsonString(binding));
        contract.setPermissionSnapshot(JsonUtils.toJsonString(permission));
        contract.setCompletionRuleSnapshot(JsonUtils.toJsonString(frozen.getCompletionRule()));
        contract.setDefinitionSnapshot(JsonUtils.toJsonString(snapshot));
    }
    private BusinessViewRevision view(String status) {
        return new BusinessViewRevision(88L, "REQUIREMENT_ANALYSIS", "REQ", 1L, "SOL", BusinessViewComponentProvider.ViewSource.PAGE,
                "PROJ_REQUIREMENT_ANALYSIS", "1", null, JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"),
                "QUERY", "COMMAND", "PERMISSION", null, null, 1, status, Set.of());
    }
    @Test void readsStageNativeFromFrozenContractWithoutGuessingByStageCode() {
        var result = service.getContext(9L, "S4", actor);
        assertEquals("STAGE_NATIVE", result.bindingType()); assertEquals(90L, result.stageId()); assertNull(result.recoverableError());
        verifyNoInteractions(views, owner);
    }

    private void approvalBinding() {
        binding("APPROVAL");
        var snapshot = JsonUtils.parseObject(contract.getDefinitionSnapshot(), TemplateExecutionSnapshot.class);
        var binding = snapshot.getStages().getFirst().getBinding();
        binding.setBusinessViewSnapshot(null); binding.setApprovalDefinitionKey("review");
        binding.setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"review:1\"}"));
        contract.setBindingSnapshot(JsonUtils.toJsonString(binding));
        contract.setDefinitionSnapshot(JsonUtils.toJsonString(snapshot));
    }

    @Test void approvalUsesFrozenBindingWithoutRequiringABusinessPageAndRespectsStageEligibility() {
        approvalBinding();
        var fact = new cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact(
                cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.NOT_SATISFIED,
                "RUNNING","pi","review:1",null);
        var view = new cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.View("review","review:1",101L,fact);
        when(approvals.view(eq(1L),eq(execution),any())).thenReturn(view);
        var result = service.getContext(9L,"S4",actor);
        assertEquals(view,result.approval()); assertNull(result.businessView()); assertNull(result.recoverableError());
        assertFalse(result.readonly()); assertEquals(Set.of("QUERY","APPROVAL"),result.ownerActions());
        stage.setStatus("DONE");
        assertTrue(service.getContext(9L,"S4",actor).readonly());
        assertEquals(Set.of("QUERY"),service.getContext(9L,"S4",actor).ownerActions());
        verify(approvals,atLeastOnce()).view(eq(1L),eq(execution),argThat(binding -> "review".equals(binding.getApprovalDefinitionKey())
                && "review:1".equals(binding.getParameters().path("processDefinitionId").asText())));
        verifyNoInteractions(views,owner);
    }

    @Test void unknownApprovalIsVisibleAsAnUnavailableResultWithoutGrantingActions() {
        approvalBinding();
        var fact = cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact.unknown("STAGE_APPROVAL_FACT_UNAVAILABLE");
        when(approvals.view(eq(1L),eq(execution),any())).thenReturn(
                new cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.View("review","review:1",101L,fact));
        var result = service.getContext(9L,"S4",actor);
        assertEquals("STAGE_APPROVAL_FACT_UNAVAILABLE",result.recoverableError());
        assertTrue(result.readonly()); assertNull(result.approval().current().processInstanceId());
        verifyNoInteractions(views,owner);
    }
    @Test void resolvesExactViewAndPassesStageIdentityOnlyToOwner() {
        binding("BUSINESS_OBJECT");
        var result = service.getContext(9L, "S4", actor);
        assertEquals(88L, result.businessView().id()); assertFalse(result.readonly());
        verify(views).getRevision(new BusinessViewQueryApi.Query(88L, BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
        assertEquals(execution, result.execution());
        verify(owner).inspectStage(new StageBusinessViewProvider.Context(1L, 7L, 9L, 90L, "PRE_04_REQUIREMENT_ANALYSIS", "CREATE_ON_FIRST_ACTION", execution));
    }
    @Test void permissionDeclarationNeitherRequiresEveryActionNorGrantsActionsDeniedByOwner() {
        binding("BUSINESS_OBJECT");
        var snapshot = JsonUtils.parseObject(contract.getDefinitionSnapshot(), TemplateExecutionSnapshot.class);
        var permission = snapshot.getStages().getFirst().getPermission();
        permission.setPolicySnapshot(JsonUtils.parseTree("{\"requiredActions\":[\"QUERY\",\"DELETE\"]}"));
        contract.setPermissionSnapshot(JsonUtils.toJsonString(permission));
        contract.setDefinitionSnapshot(JsonUtils.toJsonString(snapshot));

        var result = service.getContext(9L, "S4", actor);

        assertNull(result.recoverableError());
        assertFalse(result.readonly());
        assertEquals(Set.of("QUERY", "PATCH_FORM"), result.ownerActions());
        when(owner.inspectStage(any())).thenReturn(new StageBusinessViewProvider.Result(Set.of()));
        assertEquals("OWNER_CONTEXT_FORBIDDEN", service.getContext(9L, "S4", actor).recoverableError());
    }

    @Test void activeStageProjectionDoesNotOverrideInactiveExecution() {
        binding("BUSINESS_OBJECT");
        when(executions.inspectStage(any())).thenReturn(new ProjectStageExecutionContext(9L, 1, 90L, 1, 99L, 1, 100L, 101L, 1, 2, false));
        var result = service.getContext(9L, "S4", actor);
        assertTrue(result.readonly()); assertEquals(Set.of("QUERY"), result.ownerActions());
        assertNotNull(result.businessView());
    }
    @Test void unavailableExecutionKeepsFrozenViewReadableWithoutWriteActions() {
        binding("BUSINESS_OBJECT");
        when(executions.inspectStage(any())).thenThrow(new IllegalArgumentException("stale round"));
        var result = service.getContext(9L, "S4", actor);
        assertTrue(result.readonly()); assertEquals(Set.of("QUERY"), result.ownerActions());
        assertEquals("STAGE_EXECUTION_UNAVAILABLE", result.recoverableError()); assertNull(result.execution());
        assertEquals(88L, result.businessView().id());
        stage.setStatus("DONE");
        var history = service.getContext(9L, "S4", actor);
        assertTrue(history.readonly()); assertNotNull(history.businessView()); assertNull(history.recoverableError());
    }
    @Test void disabledViewCannotGrantWriteActions() {
        binding("BUSINESS_OBJECT"); when(views.getRevision(any())).thenReturn(view("DISABLED"));
        var result = service.getContext(9L, "S4", actor);
        assertTrue(result.readonly()); assertEquals(Set.of("QUERY"), result.ownerActions());
    }
    @Test void onlyActiveStagesOfferWriteActionsWithoutDependingOnSingleCurrentStage() {
        binding("BUSINESS_OBJECT");
        for (String status : List.of("PENDING", "DONE")) {
            stage.setStatus(status);
            var context = service.getContext(9L, "S4", actor);
            assertTrue(context.readonly()); assertEquals(Set.of("QUERY"), context.ownerActions());
        }
        stage.setStatus("ACTIVE");
        var project = projects.getProject(9L, actor); project.setCurrentStage("ANOTHER_ACTIVE_STAGE");
        assertFalse(service.getContext(9L, "S4", actor).readonly());
    }
    @Test void sourceAssetRevisionIdsDoNotControlRuntimeIdentity() {
        binding("BUSINESS_OBJECT");
        stage.setDefinitionRevisionId(500L); contract.setDefinitionRevisionId(600L);
        assertFalse(service.getContext(9L, "S4", actor).readonly());
        contract.setSourceNodeKey("missing-node");
        assertEquals("STAGE_CONTRACT_STALE", service.getContext(9L, "S4", actor).recoverableError());
    }
    @Test void aChangedComponentAtTheSameViewIdIsNotExecuted() {
        binding("BUSINESS_OBJECT");
        var original = view("PUBLISHED");
        when(views.getRevision(any())).thenReturn(new BusinessViewRevision(original.id(), original.entityType(), original.viewKey(),
                original.revisionNo(), original.ownerContext(), original.viewSource(), "DIFFERENT_COMPONENT", original.componentVersion(),
                original.dynamicFormRevisionId(), original.contextSchema(), original.supportedActions(), original.queryProviderKey(),
                original.commandProviderKey(), original.permissionProviderKey(), original.publishedAt(), original.disabledAt(),
                original.version(), original.status(), original.allowedActions()));
        assertEquals("VIEW_IDENTITY_MISMATCH", service.getContext(9L, "S4", actor).recoverableError());
        verify(owner, never()).inspectStage(any());
    }
    @Test void rejectsForeignOrStaleStageContract() {
        contract.setTenantId(2L);
        assertEquals("STAGE_CONTRACT_STALE", service.getContext(9L, "S4", actor).recoverableError());
        verifyNoInteractions(views, owner);
    }
    @Test void doesNotResolveLatestWhenFrozenBindingDisagrees() {
        contract.setBindingSnapshot("{}");
        assertEquals("STAGE_BINDING_SNAPSHOT_MISMATCH", service.getContext(9L, "S4", actor).recoverableError());
        verifyNoInteractions(views, owner);
    }
    @Test void missingOrAmbiguousCurrentContractIsNotNativeFallback() {
        when(graph.selectContracts(any())).thenReturn(List.of(contract, contract));
        assertEquals("STAGE_CONTRACT_NOT_FROZEN", service.getContext(9L, "S4", actor).recoverableError());
        verifyNoInteractions(views, owner);
    }
    @Test void ownerDenialNeverReturnsAUsableView() {
        binding("BUSINESS_OBJECT"); when(owner.inspectStage(any())).thenReturn(new StageBusinessViewProvider.Result(Set.of()));
        var result = service.getContext(9L, "S4", actor);
        assertEquals("OWNER_CONTEXT_FORBIDDEN", result.recoverableError()); assertNull(result.businessView());
    }
    @Test void projectScopeDenialPrecedesGraphLookup() {
        when(projects.getProject(9L, actor)).thenThrow(new IllegalArgumentException("forbidden"));
        assertThrows(IllegalArgumentException.class, () -> service.getContext(9L, "S4", actor)); verifyNoInteractions(graph, views, owner);
    }
    @Test void readEndpointDoesNotPretendToExecuteCreateOnEnter() {
        binding("BUSINESS_OBJECT");
        contract.setBindingSnapshot(contract.getBindingSnapshot().replace("CREATE_ON_FIRST_ACTION", "CREATE_ON_ENTER"));
        contract.setDefinitionSnapshot(contract.getDefinitionSnapshot().replace("CREATE_ON_FIRST_ACTION", "CREATE_ON_ENTER"));
        assertEquals("STAGE_ENTER_COMMAND_NOT_CONNECTED", service.getContext(9L, "S4", actor).recoverableError());
        verify(owner, never()).inspectStage(any());
    }
}
