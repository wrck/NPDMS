package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectOperationDispatcherTest {
    @Test void newObjectKindUsesExactOriginalVersionAndOriginalTransactionalExecutor() {
        var contexts = mock(ProjectOperationContextResolver.class);
        var executor = mock(ProjectControlledOperationExecutor.class);
        var adapters = mock(ProjectOperationAdapters.class);
        var plans = mock(ProjectPlanVersionMapper.class);
        var rounds = mock(ProjectNodeExecutionMapper.class);
        var adapter = mock(ProjectBusinessOperationCommandAdapter.class);
        String code = "OTHER.NON_NUMERIC_OBJECT.SAVE";
        var selection = new ProjectBusinessExecutionSelection(null,
                new ProjectStageExecutionContext(3L, 1, 4L, 1, 5L, 1, 6L, 7L, 1, 1, true));
        var command = new ProjectOperationCommand(3L, "STAGE", 4L, selection, "object:alpha", 1, "fact", JsonUtils.parseTree("{}"), "same-key");
        when(contexts.resolve(3L, "STAGE", 4L, null)).thenReturn(new ProjectOperationContextResolver.Context(
                1L, 2L, null, null, null, null, null, false, "CURRENT_ROUND_CHANGED"));
        var plan = new ProjectPlanVersionDO(); plan.setId(6L); plan.setTenantId(1L); plan.setProjectId(3L); plan.setStatus("SUPERSEDED");
        var contract = Map.of("version", 1, "programs", Map.of(), "operations", List.of(Map.of("operationCode", code,
                "operationVersion", 7, "pre", Map.of("mode", "NONE"), "post", Map.of("mode", "NONE"))));
        plan.setExecutionSnapshot(JsonUtils.toJsonString(Map.of("executionSchemaVersion", 2, "stages", List.of(
                Map.of("nodeKey", "original-node", "binding", Map.of("operationContract", contract))))));
        var round = new ProjectNodeExecutionDO(); round.setId(7L); round.setTenantId(1L); round.setProjectId(3L);
        round.setNodeKind("STAGE"); round.setNodeInstanceId(4L); round.setNodeKey("original-node");
        when(plans.selectById(6L)).thenReturn(plan); when(rounds.selectById(7L)).thenReturn(round);
        var dispatcher = new ProjectOperationDispatcher(contexts, executor, adapters, plans, rounds);
        var result = new ProjectOperationResult("OTHER", "NON_NUMERIC_OBJECT", "object:alpha", null, 2, "next", "SAVED", null, false);
        when(adapters.require(code, 7)).thenReturn(adapter); when(adapter.invoke(code, command)).thenReturn(result);
        when(executor.execute(eq(code), eq(7), eq(command), any())).thenAnswer(call -> {
            ProjectControlledOperationExecutor.Work work = call.getArgument(3); return work.invoke(command);
        });
        assertSame(result, dispatcher.execute(code, command));
        verify(executor).execute(eq(code), eq(7), eq(command), any()); verify(adapter).invoke(code, command);
        clearInvocations(executor, adapters, adapter);
        assertThrows(RuntimeException.class, () -> dispatcher.execute("OTHER.UNKNOWN", command));
        plan.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> dispatcher.execute(code, command));
        verifyNoInteractions(executor, adapters, adapter);
    }
}
