package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequirementAnalysisExecutionBindingTest {
    private final ProjectNodeExecutionApi api = mock(ProjectNodeExecutionApi.class);
    private final ProjectWorkBindingFactApi bindings = mock(ProjectWorkBindingFactApi.class);
    private final ProjectBusinessExecutionApi businessExecutions = mock(ProjectBusinessExecutionApi.class);
    private final RequirementAnalysisExecutionBinding service = new RequirementAnalysisExecutionBinding(api,bindings,businessExecutions);
    private final ProjectWorkBindingFact binding = new ProjectWorkBindingFact(100L, 1, 200L, 1, 300L, 1,
            700L, 1, "BUSINESS_OBJECT", "SOL", "REQUIREMENT_ANALYSIS", "PRE_04_REQUIREMENT_ANALYSIS",
            null, null, null, null, 800L, 1,
            "{\"schemaVersion\":2,\"dynamicFormTemplateId\":701,\"dynamicFormTemplateRevisionId\":702}",701L,702L,1,1);

    @BeforeEach void setup() { when(bindings.inspectTask(any())).thenReturn(binding); }

    @Test void sameRoundAllowsCurrentVersionsButLocksObservedContext() {
        PreparationDO root = root();
        var current = context(400L, 500L, 2, true);
        when(api.inspect(any())).thenReturn(current);
        assertTrue(service.canWrite(root));
        service.lockForWrite(root);
        verify(businessExecutions).lockForWrite(argThat(request -> request.projectId()==100L
                && "SOL".equals(request.ownerContext()) && "REQUIREMENT_ANALYSIS".equals(request.objectType())
                && current.equals(request.selection().task()) && request.selection().stage()==null));
    }

    @Test void sharedWriteGuardDenialCannotFallBackToOriginOrStandaloneResolution() {
        var root = root();
        String original = root.getTemplateSnapshot();
        when(api.inspect(any())).thenReturn(context(400L, 501L, 2, true));
        doThrow(new IllegalStateException("execution denied")).when(businessExecutions).lockForWrite(any());

        assertThrows(IllegalStateException.class, () -> service.lockForWrite(root));

        verify(api,never()).lockAndRevalidate(any());
        verify(bindings,never()).inspect(any());
        assertEquals(original,root.getTemplateSnapshot());
    }

    @Test void explicitTaskLocksTheRequestedRoundBeforeReadingItsBinding() {
        var requested = context(400L,501L,2,true);
        assertEquals(binding,service.lockRequested(100L,new ProjectBusinessExecutionSelection(requested,null)));
        var order = inOrder(api,bindings);
        order.verify(api).lockAndRevalidate(requested);
        order.verify(bindings).inspectTask(argThat(query -> query.projectId()==100L && query.projectTaskId()==200L));
        verify(bindings,never()).inspect(any());
        verify(bindings,never()).inspectStage(any());

        when(api.lockAndRevalidate(requested)).thenThrow(new IllegalStateException("stale task"));
        clearInvocations(bindings);
        assertThrows(IllegalStateException.class, () -> service.lockRequested(100L,new ProjectBusinessExecutionSelection(requested,null)));
        verifyNoInteractions(bindings);
    }

    @Test void mixedEmptyAndForeignSelectionsAreRejectedBeforeNodeLookup() {
        var task = context(400L,501L,2,true);
        assertThrows(RuntimeException.class, () -> service.lockRequested(100L,new ProjectBusinessExecutionSelection(task,stageContext(500L,1))));
        assertThrows(RuntimeException.class, () -> service.lockRequested(100L,new ProjectBusinessExecutionSelection(null,null)));
        assertThrows(RuntimeException.class, () -> service.lockRequested(999L,new ProjectBusinessExecutionSelection(task,null)));
        verifyNoInteractions(api,bindings,businessExecutions);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"TASK","STAGE"})
    void explicitlySharedHandlingUsesTheChosenNodeWithoutRewritingOrigin(String kind) {
        var root = root();
        String origin = root.getTemplateSnapshot();
        ProjectBusinessExecutionSelection selected;
        if ("TASK".equals(kind)) {
            var other = bindingWith("projectTaskId",201L);
            var task = new ProjectTaskExecutionContext(100L,1,201L,1,300L,1,400L,501L,1,2,600L,1,true,null);
            when(bindings.inspectTask(any())).thenReturn(other);
            when(api.inspect(any())).thenReturn(task);
            selected = new ProjectBusinessExecutionSelection(task,null);
        } else {
            when(bindings.inspectStage(any())).thenReturn(stageBinding());
            var stage = stageContext(501L,1);
            when(api.inspectStage(any())).thenReturn(stage);
            selected = new ProjectBusinessExecutionSelection(null,stage);
        }

        assertTrue(service.canWrite(root,selected));
        service.lockForWrite(root,selected);

        verify(businessExecutions).lockForWrite(argThat(request -> selected.equals(request.selection())));
        assertEquals(origin,root.getTemplateSnapshot());
        if ("TASK".equals(kind)) {
            when(bindings.inspectTask(any())).thenReturn(bindingWith("dynamicFormTemplateRevisionId",703L));
            assertFalse(service.canWrite(root,selected));
            assertThrows(RuntimeException.class, () -> service.lockForWrite(root,selected));
        }
    }

    @Test void businessDraftCanContinueWithTheLatestTaskExecutionWithoutOriginRoundComparison() {
        PreparationDO root = root();
        when(api.inspect(any())).thenReturn(context(400L, 501L, 1, true));
        assertTrue(service.canWrite(root));
        service.lockForWrite(root);
        when(api.inspect(any())).thenReturn(context(401L, 500L, 1, true));
        assertTrue(service.canWrite(root));
        verify(businessExecutions).lockForWrite(argThat(request ->
                context(400L, 501L, 1, true).equals(request.selection().task())));
    }

    @Test void missingSnapshotInactiveOrUnavailableContextDoesNotAdvertiseWrite() {
        var root = root();
        when(api.inspect(any())).thenReturn(context(400L, 500L, 1, false));
        assertFalse(service.canWrite(root));
        assertFalse(service.canCreate(binding));
        when(api.inspect(any())).thenThrow(new IllegalStateException("unavailable"));
        assertFalse(service.canCreate(binding));
        root.setTemplateSnapshot("{}");
        assertFalse(service.canWrite(root));
        assertThrows(RuntimeException.class, () -> service.lockForWrite(root));
    }

    @Test void renewedPermissionContractUsesTheSameTasksCurrentBindingWithoutChangingItsOriginSnapshot() {
        var root = root(); String original = root.getTemplateSnapshot();
        var changed = bindingWith("executionContractId",301L);
        when(bindings.inspectTask(any())).thenReturn(changed);
        var current = new ProjectTaskExecutionContext(100L,2,200L,3,301L,2,401L,501L,2,2,600L,2,true,java.time.LocalDateTime.of(2026,9,14,10,0));
        when(api.inspect(any())).thenReturn(current);
        assertTrue(service.canWrite(root)); service.lockForWrite(root);
        verify(bindings,times(2)).inspectTask(argThat(query -> query.projectId()==100L && query.projectTaskId()==200L));
        verify(api,times(2)).inspect(argThat(query -> query.executionContractId()==301L && query.taskId()==200L));
        verify(businessExecutions).lockForWrite(argThat(request -> current.equals(request.selection().task())));
        assertEquals(original,root.getTemplateSnapshot());
    }

    @Test void differentTaskOrOwnerFormBindingCannotReuseAnExistingBusinessRecord() {
        var root = root();
        when(bindings.inspectTask(any())).thenReturn(bindingWith("projectTaskId",201L));
        assertFalse(service.canWrite(root));
        when(bindings.inspectTask(any())).thenReturn(bindingWith("dynamicFormTemplateRevisionId",703L));
        assertFalse(service.canWrite(root));
        verify(api,never()).inspect(any());
        assertThrows(RuntimeException.class,()->service.lockForWrite(root));
    }

    @Test void unavailableCurrentBindingIsNotTreatedAsTheOriginContractStillBeingValid() {
        when(bindings.inspectTask(any())).thenThrow(new IllegalStateException("current binding unavailable"));
        assertFalse(service.canWrite(root()));
        assertThrows(RuntimeException.class,()->service.lockForWrite(root()));
        verify(api,never()).inspect(any());
    }

    @Test void stageFreezesItsOwnIdentityAndBeginsRealHandlingWithoutBorrowingATask() {
        var stage = stageBinding();
        var observed = stageContext(500L, 1);
        var started = stageContext(500L, 2);
        when(api.inspectStage(any())).thenReturn(observed, started);
        var frozen = service.lockCurrent(stage);
        var restored = JsonUtils.parseObject(service.freeze(frozen), RequirementAnalysisExecutionBinding.Frozen.class);
        assertNull(restored.execution());
        assertNull(restored.binding().projectTaskId());
        assertEquals(600L, restored.binding().projectStageId());
        assertEquals(started, restored.stageExecution());
        verify(api,times(2)).inspectStage(argThat(query -> query.stageId()==600L && query.executionContractId()==300L));
        verify(businessExecutions).lockForWrite(argThat(request -> request.projectId()==100L
                && "SOL".equals(request.ownerContext()) && "REQUIREMENT_ANALYSIS".equals(request.objectType())
                && request.selection().task()==null && observed.equals(request.selection().stage())));
        verify(api,never()).inspect(any());
        verify(api,never()).lockAndRevalidate(any());
    }

    @Test void stageReworkResolvesCurrentRoundButDoesNotRewriteBusinessOrigin() {
        var stage = stageBinding();
        var root = new PreparationDO(); root.setProjectId(100L);
        root.setTemplateSnapshot(service.freeze(new RequirementAnalysisExecutionBinding.Frozen(stage, null, stageContext(500L, 1))));
        String original = root.getTemplateSnapshot();
        when(bindings.inspectStage(any())).thenReturn(stage);
        var current = stageContext(501L, 4);
        when(api.inspectStage(any())).thenReturn(current);
        assertTrue(service.canWrite(root));
        service.lockForWrite(root);
        verify(businessExecutions).lockForWrite(argThat(request -> current.equals(request.selection().stage())));
        verify(bindings,never()).inspectTask(any());
        assertEquals(original, root.getTemplateSnapshot());
        when(bindings.lockAndRevalidateStage(any())).thenReturn(stage);
        assertEquals(stage, service.lockBinding(stage));
        verify(bindings).lockAndRevalidateStage(argThat(query -> query.projectStageId()==600L
                && query.expectedProjectStageVersion()==1 && query.executionContractId()==300L));
        verify(bindings,never()).lockAndRevalidate(any());
    }

    @Test void invalidOrUnavailableStageCannotFallBackToTaskHandling() {
        var stage = stageBinding();
        assertThrows(RuntimeException.class, () -> service.freeze(new RequirementAnalysisExecutionBinding.Frozen(stage, context(400L,500L,1,true), null)));
        assertThrows(RuntimeException.class, () -> service.lockRequested(999L, new ProjectBusinessExecutionSelection(null,stageContext(500L,1))));
        when(api.inspectStage(any())).thenThrow(new IllegalStateException("stage unavailable"));
        assertFalse(service.canCreate(stage));
        verify(api,never()).inspect(any());
        verify(api,never()).lockAndRevalidateStage(any());
    }

    private ProjectWorkBindingFact stageBinding() {
        var json = (tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(binding));
        json.putNull("projectTaskId"); json.putNull("projectTaskVersion");
        json.put("projectStageId",600L); json.put("projectStageVersion",1);
        return JsonUtils.parseObject(json.toString(), ProjectWorkBindingFact.class);
    }

    private ProjectStageExecutionContext stageContext(long execution, int version) {
        return new ProjectStageExecutionContext(100L,1,600L,1,300L,1,400L,execution,version,2,true);
    }

    private ProjectWorkBindingFact bindingWith(String field,long value) {
        var json = (tools.jackson.databind.node.ObjectNode)cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(binding));
        json.put(field,value);
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(json.toString(),ProjectWorkBindingFact.class);
    }

    private PreparationDO root() {
        var root = new PreparationDO(); root.setProjectId(100L);
        root.setTemplateSnapshot(service.freeze(binding, context(400L, 500L, 1, true)));
        return root;
    }

    private ProjectTaskExecutionContext context(long plan, long execution, int version, boolean writable) {
        return new ProjectTaskExecutionContext(100L, 1, 200L, version, 300L, 1, plan,
                execution, version, 1, 600L, 1, writable, java.time.LocalDateTime.of(2026,9,14,9,0));
    }
}
