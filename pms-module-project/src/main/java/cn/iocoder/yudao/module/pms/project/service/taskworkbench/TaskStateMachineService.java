package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.TaskStateMachineSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineDefinition;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachineRevisionLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachineTenantQuery;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.PublishTaskStateMachineCommand;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;

/** 租户级任务状态机草稿、查询和append-only发布服务。 */
@Service
@RequiredArgsConstructor
public class TaskStateMachineService {

    private static final String MANAGE_PERMISSION = "pms:project-task-state:manage";
    private static final String PUBLISH_SCOPE =
            "POST:/api/v1/pms/project-task-state-machines/{id}/actions/publish";

    private final TaskStateMachineMapper mapper;
    private final PermissionCommonApi permissionApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;

    public TaskStateMachineDefinition getPublished(Long tenantId, Long actorId) {
        requireManager(actorId);
        if (tenantId == null || tenantId < 0) throw exception(PROJECT_TASK_COMMAND_INVALID);
        TaskStateMachineDefinition definition = mapper.selectPublished(
                TaskStateMachinePublishedQuery.builder().tenantId(tenantId)
                        .effectiveAt(LocalDateTime.now()).build());
        if (definition == null) throw exception(PROJECT_TASK_COMMAND_INVALID);
        return definition;
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskStateMachineDefinition createDraft(TaskStateMachineSaveReqVO request, TaskWorkbenchActor actor) {
        requireManager(actor.actorId());
        if (request == null || request.getEffectiveFrom() == null || request.getTransitions() == null
                || request.getTransitions().isEmpty()) throw exception(PROJECT_TASK_COMMAND_INVALID);
        TaskStateMachineRevisionDO latest = mapper.selectLatestRevisionForUpdate(
                new TaskStateMachineTenantQuery(actor.tenantId()));
        TaskStateMachineRevisionDO revision = new TaskStateMachineRevisionDO();
        revision.setId(IdWorker.getId());
        revision.setTenantId(actor.tenantId());
        revision.setRevisionNo(latest == null ? 1 : latest.getRevisionNo() + 1);
        revision.setStatus("DRAFT");
        revision.setEffectiveFrom(request.getEffectiveFrom());
        revision.setVersion(0);
        revision.setCreator(String.valueOf(actor.actorId()));
        revision.setUpdater(String.valueOf(actor.actorId()));
        if (mapper.insertDraft(revision) != 1) throw new IllegalStateException("TASK_STATE_DRAFT_WRITE_FAILED");
        List<TaskStateTransitionDO> transitions = request.getTransitions().stream()
                .map(item -> transition(revision, item, actor.actorId())).toList();
        for (TaskStateTransitionDO transition : transitions) {
            if (mapper.insertTransition(transition) != 1) {
                throw new IllegalStateException("TASK_STATE_TRANSITION_WRITE_FAILED");
            }
        }
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PROJECT_TASK_STATE_MACHINE_DRAFT_CREATE", "TaskStateMachineRevision",
                String.valueOf(revision.getId()), "SUCCESS", Map.of(
                        "revisionNo", revision.getRevisionNo(),
                        "transitionCount", transitions.size()));
        return new TaskStateMachineDefinition(revision, transitions);
    }

    public TaskStateMachineDefinition publish(PublishTaskStateMachineCommand command, TaskWorkbenchActor actor) {
        requireManager(actor.actorId());
        validatePublish(command, actor);
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), PUBLISH_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), TaskStateMachineDefinition.class,
                () -> publishOnce(command, actor), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        "PROJECT_TASK_STATE_MACHINE_PUBLISH", "TaskStateMachineRevision",
                        String.valueOf(result.revision().getId()), actor.correlationId(),
                        JsonUtils.toJsonString(Map.of("revisionId", result.revision().getId(),
                                "revisionNo", result.revision().getRevisionNo(),
                                "version", result.revision().getVersion())), null, null));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }

    private TaskStateMachineDefinition publishOnce(PublishTaskStateMachineCommand command,
                                                    TaskWorkbenchActor actor) {
        try {
            if (mapper.publishIfValid(new TaskStateMachinePublishUpdate(actor.tenantId(), command.revisionId(),
                    command.expectedVersion(), actor.actorId(), LocalDateTime.now(),
                    String.valueOf(actor.actorId()))) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        TaskStateMachineRevisionLockQuery query = new TaskStateMachineRevisionLockQuery(
                actor.tenantId(), command.revisionId());
        return new TaskStateMachineDefinition(mapper.selectRevisionForUpdate(query), mapper.selectTransitions(query));
    }

    private TaskStateTransitionDO transition(TaskStateMachineRevisionDO revision,
                                             TaskStateMachineSaveReqVO.Transition input, Long actorId) {
        TaskStateTransitionDO transition = new TaskStateTransitionDO();
        transition.setId(IdWorker.getId());
        transition.setTenantId(revision.getTenantId());
        transition.setRevisionId(revision.getId());
        transition.setFromStatusCode(input.getFromStatusCode().trim());
        transition.setActionCode(input.getActionCode().trim().toUpperCase(java.util.Locale.ROOT));
        transition.setToStatusCode(input.getToStatusCode().trim());
        transition.setStandardStatusMapping(input.getStandardStatusMapping().trim());
        transition.setAllowedRoleCode(input.getAllowedRoleCode().trim());
        transition.setEntryCondition(input.getEntryCondition());
        transition.setExitCondition(input.getExitCondition());
        transition.setVersion(0);
        transition.setCreator(String.valueOf(actorId));
        transition.setUpdater(String.valueOf(actorId));
        return transition;
    }

    private void validatePublish(PublishTaskStateMachineCommand command, TaskWorkbenchActor actor) {
        if (command == null || command.revisionId() == null || command.revisionId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || actor == null || actor.tenantId() == null || actor.actorId() == null) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
    }

    private void requireManager(Long actorId) {
        if (actorId == null || !permissionApi.hasAnyPermissions(actorId, MANAGE_PERMISSION)) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
    }
}
