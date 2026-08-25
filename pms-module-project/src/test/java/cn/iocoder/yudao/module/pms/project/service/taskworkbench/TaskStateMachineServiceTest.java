package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.TaskStateMachineSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineDefinition;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.PublishTaskStateMachineCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStateMachineServiceTest {

    @Mock TaskStateMachineMapper mapper;
    @Mock PermissionCommonApi permissionApi;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    private TaskStateMachineService service;

    @BeforeEach
    void setUp() {
        service = new TaskStateMachineService(mapper, permissionApi, commandExecutionApi, operationAuditApi);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task-state:manage")).thenReturn(true);
    }

    @Test
    void createDraftUsesNextTenantRevisionAndInsertsFrozenTransitions() {
        TaskStateMachineRevisionDO latest = revision(81L, 3, "PUBLISHED", 1);
        when(mapper.selectLatestRevisionForUpdate(any())).thenReturn(latest);
        when(mapper.insertDraft(any())).thenReturn(1);
        when(mapper.insertTransition(any())).thenReturn(1);

        TaskStateMachineDefinition result = service.createDraft(request(), actor());

        assertEquals(4, result.revision().getRevisionNo());
        assertEquals("DRAFT", result.revision().getStatus());
        assertEquals(1, result.transitions().size());
        verify(mapper).insertTransition(any());
        verify(operationAuditApi).record(eq(0L), eq(9L), eq("corr-1"),
                eq("PROJECT_TASK_STATE_MACHINE_DRAFT_CREATE"), eq("TaskStateMachineRevision"),
                any(), eq("SUCCESS"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishReusesLockedValidationAndVersionCas() {
        TaskStateMachineRevisionDO published = revision(82L, 4, "PUBLISHED", 1);
        TaskStateTransitionDO transition = transition();
        when(mapper.publishIfValid(any())).thenReturn(1);
        when(mapper.selectRevisionForUpdate(any())).thenReturn(published);
        when(mapper.selectTransitions(any())).thenReturn(List.of(transition));
        when(commandExecutionApi.execute(any(), any(), eq(TaskStateMachineDefinition.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<TaskStateMachineDefinition> operation = invocation.getArgument(3);
                    Function<TaskStateMachineDefinition, PlatformCommandExecutionApi.SuccessFacts> facts =
                            invocation.getArgument(4);
                    TaskStateMachineDefinition result = operation.get();
                    facts.apply(result);
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });

        TaskStateMachineDefinition result = service.publish(new PublishTaskStateMachineCommand(
                82L, 0, "publish-key", "a".repeat(64)), actor());

        assertEquals("PUBLISHED", result.revision().getStatus());
        verify(mapper).publishIfValid(any());
    }

    private TaskStateMachineSaveReqVO request() {
        TaskStateMachineSaveReqVO request = new TaskStateMachineSaveReqVO();
        request.setEffectiveFrom(LocalDateTime.now().plusDays(1));
        TaskStateMachineSaveReqVO.Transition transition = new TaskStateMachineSaveReqVO.Transition();
        transition.setFromStatusCode("WAITING_VENDOR");
        transition.setActionCode("submit");
        transition.setToStatusCode("PENDING_ACCEPT");
        transition.setStandardStatusMapping("PENDING_ACCEPT");
        transition.setAllowedRoleCode("CURRENT_EFFECTIVE_ASSIGNEE");
        transition.setEntryCondition("{\"schemaVersion\":1}");
        transition.setExitCondition("{\"schemaVersion\":1}");
        request.setTransitions(List.of(transition));
        return request;
    }

    private TaskStateMachineRevisionDO revision(Long id, int revisionNo, String status, int version) {
        TaskStateMachineRevisionDO revision = new TaskStateMachineRevisionDO();
        revision.setId(id);
        revision.setTenantId(0L);
        revision.setRevisionNo(revisionNo);
        revision.setStatus(status);
        revision.setVersion(version);
        return revision;
    }

    private TaskStateTransitionDO transition() {
        TaskStateTransitionDO transition = new TaskStateTransitionDO();
        transition.setId(1L);
        transition.setRevisionId(82L);
        transition.setFromStatusCode("PENDING_START");
        transition.setActionCode("START");
        transition.setToStatusCode("IN_PROGRESS");
        return transition;
    }

    private TaskWorkbenchActor actor() {
        return new TaskWorkbenchActor(0L, 9L, "corr-1");
    }
}
