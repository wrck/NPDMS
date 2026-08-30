package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.event.SatisfactionTaskCreatedMessage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SatisfactionTaskTodoProjectionServiceTest {
    @Test
    void revalidatesFrozenProjectTaskVersionAndAppendsTodoRequested() {
        ProjectWorkBindingFactApi factApi = mock(ProjectWorkBindingFactApi.class);
        SatisfactionCollectionTaskMapper mapper = mock(SatisfactionCollectionTaskMapper.class);
        PlatformCommandExecutionApi executionApi = mock(PlatformCommandExecutionApi.class);
        AtomicReference<PlatformCommandExecutionApi.SuccessFacts> emitted = new AtomicReference<>();
        when(factApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(new ProjectSatisfactionTaskFact(
                20L, 21L, "T-SAT-SURVEY", 7, "AFTER_INITIAL_ACCEPTANCE", 30L, 31L,
                1, "RULE-1", new BigDecimal("80.00"), 99L));
        when(mapper.selectByIdForUpdate(7L, 10L)).thenReturn(task());
        when(executionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            Object response = operation.get(); emitted.set(facts.apply(response));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        });
        var service = new SatisfactionTaskTodoProjectionService(factApi, mapper, executionApi);

        service.project(event());

        assertEquals("TodoRequested", emitted.get().businessEvents().getFirst().eventType());
        assertEquals("SAT-TODO:10:1", emitted.get().businessEvents().getFirst().eventId());
    }

    private SatisfactionTaskCreatedMessage event() {
        return new SatisfactionTaskCreatedMessage("task-created", 7L, 20L, 21L, 7,
                "T-SAT-SURVEY", 10L, "SAT-10", 1, null, "ACC", "AcceptanceActivity",
                "100", 1L, "ACC", "AcceptanceActivity", "100", 1L,
                11L, 31L, 1, "RULE-1", new BigDecimal("80.00"), 99L);
    }

    private SatisfactionCollectionTaskDO task() {
        SatisfactionCollectionTaskDO row = new SatisfactionCollectionTaskDO();
        row.setId(10L); row.setTenantId(7L); row.setProjectId(20L); row.setProjectTaskId(21L);
        row.setCollectionKey("SAT-10"); row.setTaskRevisionNo(1); row.setQuestionnaireId(11L);
        row.setAssignedToUserId(99L); row.setSourceOwnerContext("ACC");
        row.setSourceObjectType("AcceptanceActivity"); row.setSourceObjectId("100");
        row.setSourceObjectVersion(1L); row.setTriggerOwnerContext("ACC");
        row.setTriggerObjectType("AcceptanceActivity"); row.setTriggerFactId("100");
        row.setTriggerFactVersion(1L);
        return row;
    }
}
