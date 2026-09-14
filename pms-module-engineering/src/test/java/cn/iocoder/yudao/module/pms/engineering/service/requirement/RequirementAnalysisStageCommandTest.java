package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisStageCommandTest {
    final RequirementAnalysisRootMapper roots = mock(RequirementAnalysisRootMapper.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final ProjectParticipantFactApi participants = mock(ProjectParticipantFactApi.class);
    final ProjectWorkBindingFactApi bindings = mock(ProjectWorkBindingFactApi.class);
    final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    final ProjectBusinessExecutionApi businessExecutions = mock(ProjectBusinessExecutionApi.class);
    final DynamicFormBusinessInstanceApi forms = mock(DynamicFormBusinessInstanceApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final EngineeringRuleReevaluationEvents events = mock(EngineeringRuleReevaluationEvents.class);
    final TransactionTemplate transaction = mock(TransactionTemplate.class);
    final RequirementAnalysisDynamicFormPolicyProvider policy = mock(RequirementAnalysisDynamicFormPolicyProvider.class);
    final RequirementAnalysisExecutionBinding executionBinding = new RequirementAnalysisExecutionBinding(executions,bindings,businessExecutions);
    final RequirementAnalysisDynamicFormCommandService service = new RequirementAnalysisDynamicFormCommandService(
            roots,scopes,participants,bindings,forms,policy,
            permissions,commands,audit,transaction,executionBinding,events);
    final RequirementAnalysisDynamicFormCommandService.Actor actor = new RequirementAnalysisDynamicFormCommandService.Actor(1L,9L,"stage-business");
    final ProjectStageExecutionContext observed = new ProjectStageExecutionContext(100L,1,600L,1,300L,1,400L,500L,1,2,true);
    final ProjectStageExecutionContext started = new ProjectStageExecutionContext(100L,1,600L,1,300L,1,400L,500L,2,2,true);
    final ProjectWorkBindingFact binding = new ProjectWorkBindingFact(100L,1,null,null,300L,1,700L,null,
            "BUSINESS_OBJECT","SOL","REQUIREMENT_ANALYSIS","PRE_04_REQUIREMENT_ANALYSIS",null,null,null,null,701L,1,
            "{\"schemaVersion\":2,\"dynamicFormTemplateId\":800,\"dynamicFormTemplateRevisionId\":801}",800L,801L,1,3,600L,1);
    final DynamicFormProviderKey provider = new DynamicFormProviderKey("SOL","REQUIREMENT_ANALYSIS");
    PreparationDO created;

    @BeforeEach void setup() {
        when(transaction.execute(any())).thenAnswer(call -> ((TransactionCallback<?>)call.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
        when(commands.execute(any(),anyString(),any(),any(),any())).thenAnswer(call ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, ((Supplier<?>)call.getArgument(3)).get()));
        when(permissions.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(true);
        var scope = new ProjectScopeResult(100L,1L,Set.of(100L),Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        var manager = new ProjectParticipantFact(100L,9L,Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),"PRIMARY","ACTIVE",null,1,1L);
        when(participants.inspect(any())).thenReturn(manager); when(participants.lockAndRevalidate(any())).thenReturn(manager);
        when(bindings.inspectStage(any())).thenReturn(binding); when(bindings.lockAndRevalidateStage(any())).thenReturn(binding);
        when(executions.inspectStage(any())).thenReturn(started);
        when(executions.lockAndRevalidateStage(any())).thenAnswer(call -> call.getArgument(0));
        when(forms.inspectRevisionForUsage(any())).thenReturn(new DynamicFormRevisionFact(1L,provider,800L,801L,1,3,
                "REQUIREMENT_ANALYSIS",DynamicFormBusinessAction.REVISION_FROZEN_USE,"FORM_CREATE_ELEMENT_PLUS","3.4.0","3.2.38","{}","[]",List.of(),null));
        when(roots.insertDynamicRoot(any())).thenAnswer(call -> { created = call.getArgument(0); return 1; });
        when(forms.createBusinessInstance(any())).thenAnswer(call -> {
            DynamicFormInstanceCreateCommand command = call.getArgument(0);
            return form(command.preallocatedInstanceId(),command.ownerKey(),DynamicFormBusinessAction.CREATE,Map.of(),true);
        });
        when(roots.selectById(any())).thenAnswer(call -> created);
        when(roots.selectForUpdate(any())).thenAnswer(call -> created);
        when(roots.updateEntityDataIfMatch(any())).thenReturn(1);
        when(roots.completeDraftIfMatch(any())).thenReturn(1);
        when(forms.inspectEntityData(any())).thenAnswer(call -> {
            DynamicFormEntityDataQuery query = call.getArgument(0);
            return form(query.context().instanceId(),query.context().ownerKey(),query.context().action(),query.entityValues(),true);
        });
        when(forms.lockAndRevalidateInstance(any())).thenAnswer(call -> ((DynamicFormInstanceRevalidationQuery)call.getArgument(0)).expectedFact());
    }

    @Test void originalOwnerCommandsCreatePatchAndCompleteUsingTheStageNotATask() {
        var initial = create();
        assertEquals("DRAFT",initial.status());
        var origin = created.getTemplateSnapshot();
        var frozen = JsonUtils.parseObject(origin,RequirementAnalysisExecutionBinding.Frozen.class);
        assertEquals(started,frozen.stageExecution()); assertNull(frozen.execution()); assertNull(frozen.binding().projectTaskId());
        var saved = service.patch(new RequirementAnalysisDynamicFormCommandService.PatchCommand(initial.preparationId(),1,7,
                Map.of("machineCount",0,"requiresCutover",false),"stage-save"),actor);
        assertEquals(2,saved.solVersion());
        assertEquals(0,RequirementAnalysisEntityData.values(created).get("machineCount"));
        var completed = service.complete(new RequirementAnalysisDynamicFormCommandService.CompleteCommand(initial.preparationId(),2,7,"stage-complete"),actor);
        assertEquals("COMPLETED",completed.status()); assertEquals(3,completed.solVersion());
        assertEquals(origin,created.getTemplateSnapshot());
        verify(executions).lockAndRevalidateStage(observed);
        verify(businessExecutions,times(3)).lockForWrite(argThat(request -> request.projectId()==100L
                && "SOL".equals(request.ownerContext()) && "REQUIREMENT_ANALYSIS".equals(request.objectType())
                && request.selection().task()==null && started.equals(request.selection().stage())));
        verify(bindings,never()).inspect(any()); verify(bindings,never()).inspectTask(any());
        verify(executions,never()).inspect(any()); verify(executions,never()).lockAndRevalidate(any());
        verify(roots).completeDraftIfMatch(argThat(command -> command.preparationId().equals(initial.preparationId())));
        verify(events,times(3)).changed(100L,"RequirementAnalysis",initial.preparationId(),9L,"stage-business");
    }

    @Test void staleStageContextIsRejectedBeforeCreatingAnyOwnerRecord() {
        when(executions.lockAndRevalidateStage(observed)).thenThrow(new IllegalStateException("stage reworked"));
        assertThrows(IllegalStateException.class,this::create);
        verify(roots,never()).insertDynamicRoot(any()); verifyNoInteractions(forms,events);
        verify(bindings,never()).inspect(any()); verify(bindings,never()).inspectStage(any());
    }

    @Test void explicitTaskCreationFreezesTheSelectedTaskInsteadOfTheDefaultProjectBinding() {
        var json = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(binding));
        json.putNull("projectStageId"); json.putNull("projectStageVersion");
        json.put("projectTaskId",200L); json.put("projectTaskVersion",1);
        var taskBinding = JsonUtils.parseObject(json.toString(),ProjectWorkBindingFact.class);
        var task = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                100L,1,200L,1,300L,1,400L,500L,1,2,600L,1,true,null);
        when(bindings.inspectTask(any())).thenReturn(taskBinding);
        when(bindings.lockAndRevalidate(any())).thenReturn(taskBinding);
        when(executions.inspect(any())).thenReturn(task);

        service.createInitial(new RequirementAnalysisDynamicFormCommandService.CreateCommand(
                100L,"selected-task",new ProjectBusinessExecutionSelection(task,null)),actor);

        var frozen = JsonUtils.parseObject(created.getTemplateSnapshot(),RequirementAnalysisExecutionBinding.Frozen.class);
        assertEquals(task,frozen.execution()); assertNull(frozen.stageExecution());
        assertEquals(200L,frozen.binding().projectTaskId());
        verify(executions).lockAndRevalidate(task);
        verify(bindings).inspectTask(argThat(query -> query.projectTaskId()==200L));
        verify(bindings,never()).inspect(any()); verify(bindings,never()).inspectStage(any());
        verify(businessExecutions).lockForWrite(argThat(request -> task.equals(request.selection().task())));
    }

    @Test void anotherTaskCanSaveAndCompleteTheSameStageOriginDraftWithoutCreatingAnotherObject() {
        var initial = create();
        String origin = created.getTemplateSnapshot();
        var json = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(binding));
        json.putNull("projectStageId"); json.putNull("projectStageVersion");
        json.put("projectTaskId",201L); json.put("projectTaskVersion",1);
        var sharedBinding = JsonUtils.parseObject(json.toString(),ProjectWorkBindingFact.class);
        var task = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                100L,1,201L,1,300L,1,400L,501L,1,2,600L,1,true,null);
        var selected = new ProjectBusinessExecutionSelection(task,null);
        when(bindings.inspectTask(any())).thenReturn(sharedBinding);
        when(bindings.lockAndRevalidate(any())).thenReturn(sharedBinding);
        when(executions.inspect(any())).thenReturn(task);

        service.patch(new RequirementAnalysisDynamicFormCommandService.PatchCommand(
                initial.preparationId(),1,7,Map.of("machineCount",2),"shared-save",selected),actor);
        service.complete(new RequirementAnalysisDynamicFormCommandService.CompleteCommand(
                initial.preparationId(),2,7,"shared-complete",selected),actor);

        assertEquals(initial.preparationId(),created.getId());
        assertEquals("COMPLETED",created.getStatusCode());
        assertEquals(origin,created.getTemplateSnapshot());
        verify(roots,times(1)).insertDynamicRoot(any());
        verify(forms,times(2)).inspectEntityData(argThat(query ->
                JsonUtils.parseTree(JsonUtils.toJsonString(selected)).equals(query.context().ownerExecutionContext())));
        verify(businessExecutions,times(2)).lockForWrite(argThat(request -> selected.equals(request.selection())));
    }

    @Test void reworkCreatesANewBusinessDraftWithTheNewStageExecutionWithoutChangingTheCompletedSource() {
        var initial = create();
        service.complete(new RequirementAnalysisDynamicFormCommandService.CompleteCommand(initial.preparationId(),1,7,"first-complete"),actor);
        var source = created;
        String before = JsonUtils.toJsonString(source);
        var latest = new ProjectStageExecutionContext(100L,2,600L,2,301L,1,400L,501L,1,3,true);
        var json = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(binding));
        json.put("executionContractId",301L); json.put("projectVersion",2); json.put("projectStageVersion",2);
        var newBinding = JsonUtils.parseObject(json.toString(),ProjectWorkBindingFact.class);
        when(bindings.inspectStage(any())).thenReturn(newBinding);
        when(bindings.lockAndRevalidateStage(any())).thenReturn(newBinding);
        when(executions.inspectStage(any())).thenReturn(latest);
        when(policy.inspectInstanceOwnerPolicy(any())).thenReturn(new DynamicFormPolicyFact(DynamicFormBusinessAction.CLONE_TARGET,true,null,1L,"DRAFT"));
        when(forms.cloneBusinessInstance(any())).thenAnswer(call -> {
            DynamicFormInstanceCloneCommand command = call.getArgument(0);
            return form(command.preallocatedTargetInstanceId(),command.targetOwnerKey(),DynamicFormBusinessAction.CLONE_TARGET,Map.of(),true);
        });
        var result = service.createRevision(new RequirementAnalysisDynamicFormCommandService.CreateRevisionCommand(
                source.getId(),source.getDynamicFormInstanceId(),source.getVersion(),7,"stage-rework",new ProjectBusinessExecutionSelection(null,latest)),actor);
        assertEquals("DRAFT",result.status()); assertEquals(2,result.businessVersion());
        assertNotEquals(source.getId(),created.getId());
        assertEquals(source.getId(),created.getSourcePreparationId());
        var frozen = JsonUtils.parseObject(created.getTemplateSnapshot(),RequirementAnalysisExecutionBinding.Frozen.class);
        assertEquals(latest,frozen.stageExecution()); assertEquals(301L,frozen.binding().executionContractId());
        assertEquals(before,JsonUtils.toJsonString(source));
        verify(forms).cloneBusinessInstance(argThat(command -> command.sourceFact().ownerKey().objectId().equals(source.getId().toString())));
        verify(executions,never()).lockAndRevalidate(any());
    }

    @Test void originalOwnerPermissionCannotBeReplacedByAValidStageContext() {
        when(permissions.hasAnyPermissions(9L,RequirementAnalysisQueryService.PERMISSION_MANAGE)).thenReturn(false);
        assertThrows(RuntimeException.class,this::create);
        verifyNoInteractions(executions,bindings,forms,events);
        verify(roots,never()).insertDynamicRoot(any());
    }

    @Test void unifiedWriteGuardRejectionPreventsInitialOwnerCreation() {
        doThrow(new IllegalStateException("view disabled or execution denied")).when(businessExecutions).lockForWrite(any());

        assertThrows(IllegalStateException.class, this::create);

        verify(roots,never()).insertDynamicRoot(any());
        verifyNoInteractions(forms,events);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PATCH", "COMPLETE", "REVISION"})
    void unifiedWriteGuardRejectionPreservesExistingOwnerRecord(String action) {
        var initial = create();
        if ("REVISION".equals(action)) created.setStatusCode("COMPLETED");
        String before = JsonUtils.toJsonString(created);
        clearInvocations(roots,forms,events);
        doThrow(new IllegalStateException("node permission or binding denied")).when(businessExecutions).lockForWrite(any());

        assertThrows(IllegalStateException.class, () -> {
            switch (action) {
                case "PATCH" -> service.patch(new RequirementAnalysisDynamicFormCommandService.PatchCommand(
                        initial.preparationId(),1,7,Map.of("machineCount",2),"denied-patch"),actor);
                case "COMPLETE" -> service.complete(new RequirementAnalysisDynamicFormCommandService.CompleteCommand(
                        initial.preparationId(),1,7,"denied-complete"),actor);
                case "REVISION" -> service.createRevision(new RequirementAnalysisDynamicFormCommandService.CreateRevisionCommand(
                        initial.preparationId(),created.getDynamicFormInstanceId(),1,7,"denied-revision",new ProjectBusinessExecutionSelection(null,started)),actor);
                default -> fail("unexpected test action");
            }
        });

        assertEquals(before,JsonUtils.toJsonString(created));
        verify(roots,never()).insertDynamicRoot(any());
        verify(roots,never()).updateEntityDataIfMatch(any());
        verify(roots,never()).completeDraftIfMatch(any());
        verify(roots,never()).clearEffectiveIfMatch(any());
        verifyNoInteractions(forms,events);
    }

    @Test void invalidFormDoesNotCompleteBusinessOrEmitACompletionWakeup() {
        var initial = create(); clearInvocations(events);
        doReturn(form(created.getDynamicFormInstanceId(),
                new DynamicFormOwnerKey("SOL","REQUIREMENT_ANALYSIS",created.getId().toString()),DynamicFormBusinessAction.COMPLETE,Map.of(),false))
                .when(forms).inspectEntityData(any());
        assertThrows(RuntimeException.class, () -> service.complete(new RequirementAnalysisDynamicFormCommandService.CompleteCommand(initial.preparationId(),1,7,"invalid-complete"),actor));
        assertEquals("DRAFT",created.getStatusCode());
        verify(roots,never()).completeDraftIfMatch(any()); verifyNoInteractions(events);
    }

    private RequirementAnalysisDynamicFormCommandService.CommandResult create() {
        return service.createInitial(new RequirementAnalysisDynamicFormCommandService.CreateCommand(100L,"stage-create",new ProjectBusinessExecutionSelection(null,observed)),actor);
    }

    private DynamicFormInstanceFact form(Long id,DynamicFormOwnerKey owner,DynamicFormBusinessAction action,Map<String,Object> values,boolean valid) {
        return new DynamicFormInstanceFact(1L,provider,owner,id,800L,801L,1,3,"FORM_CREATE_ELEMENT_PLUS","3.4.0","3.2.38",
                "{}","[]",List.of(),values,new DynamicFormValidationFact(valid ? "VALID" : "INVALID",valid ? List.of() : List.of("REQUIRED_FIELD")),
                List.of(),7,action,new DynamicFormPolicyFact(action,true,null,1L,"DRAFT"));
    }
}
