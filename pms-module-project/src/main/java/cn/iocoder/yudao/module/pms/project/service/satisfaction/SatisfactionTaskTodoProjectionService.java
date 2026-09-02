package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.event.SatisfactionTaskCreatedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SatisfactionTaskTodoProjectionService {
    private static final String TASK_CODE = "T-SAT-SURVEY";
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Transactional(rollbackFor = Exception.class)
    public void project(SatisfactionTaskCreatedMessage event) {
        require(event);
        ProjectSatisfactionTaskFact fact = workBindingFactApi.lockAndRevalidateSatisfactionTask(
                new ProjectSatisfactionTaskFactQuery(event.projectId(), event.projectTaskId(),
                        event.projectTaskVersion()));
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(event.tenantId(), event.taskId());
        if (!exact(fact, task, event)) {
            throw new IllegalStateException("SATISFACTION_TASK_TODO_IDENTITY_CONFLICT");
        }
        String key = event.taskId() + ":" + event.taskRevisionNo();
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        event.tenantId(), "ACC_SATISFACTION_TODO_REQUEST", event.assigneeUserId(), key),
                digest(event), TodoProjectionResult.class,
                () -> new TodoProjectionResult(key, event.taskId(), event.taskRevisionNo()),
                result -> todoFacts(event, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) {
            throw new IllegalStateException("SATISFACTION_TASK_TODO_IDEMPOTENCY_CONFLICT");
        }
    }

    private PlatformCommandExecutionApi.SuccessFacts todoFacts(SatisfactionTaskCreatedMessage event,
                                                                TodoProjectionResult result) {
        String eventId = "SAT-TODO:" + event.taskId() + ":" + event.taskRevisionNo();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId); payload.put("tenantId", event.tenantId());
        payload.put("ownerContext", "ACC"); payload.put("objectType", "SatisfactionCollectionTask");
        payload.put("objectId", String.valueOf(event.taskId())); payload.put("nodeCode", "SATISFACTION_COLLECTION");
        payload.put("projectId", event.projectId()); payload.put("projectTaskId", event.projectTaskId());
        payload.put("assigneeUserId", event.assigneeUserId()); payload.put("idempotencyKey", result.key());
        return new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_TODO_REQUESTED",
                "SatisfactionCollectionTask", String.valueOf(event.taskId()), result.key(),
                JsonUtils.toJsonString(Map.of("taskId", event.taskId(), "taskRevisionNo", event.taskRevisionNo())),
                List.of(new PlatformCommandExecutionApi.BusinessEvent(eventId, "TodoRequested",
                        JsonUtils.toJsonString(payload))));
    }

    private boolean exact(ProjectSatisfactionTaskFact fact, SatisfactionCollectionTaskDO task,
                          SatisfactionTaskCreatedMessage event) {
        return fact != null && task != null && TASK_CODE.equals(fact.taskCode())
                && Objects.equals(fact.projectId(), event.projectId())
                && Objects.equals(fact.projectTaskId(), event.projectTaskId())
                && Objects.equals(fact.projectTaskVersion(), event.projectTaskVersion())
                && Objects.equals(fact.currentAssigneeUserId(), event.assigneeUserId())
                && Objects.equals(task.getProjectId(), event.projectId())
                && Objects.equals(task.getProjectTaskId(), event.projectTaskId())
                && Objects.equals(task.getCollectionKey(), event.collectionKey())
                && Objects.equals(task.getTaskRevisionNo(), event.taskRevisionNo())
                && Objects.equals(task.getPriorTaskId(), event.priorTaskId())
                && Objects.equals(task.getQuestionnaireId(), event.questionnaireId())
                && Objects.equals(task.getAssignedToUserId(), event.assigneeUserId())
                && Objects.equals(task.getSourceOwnerContext(), event.sourceOwnerContext())
                && Objects.equals(task.getSourceObjectType(), event.sourceObjectType())
                && Objects.equals(task.getSourceObjectId(), event.sourceObjectId())
                && Objects.equals(task.getSourceObjectVersion(), event.sourceObjectVersion())
                && Objects.equals(task.getTriggerOwnerContext(), event.triggerOwnerContext())
                && Objects.equals(task.getTriggerObjectType(), event.triggerObjectType())
                && Objects.equals(task.getTriggerFactId(), event.triggerFactId())
                && Objects.equals(task.getTriggerFactVersion(), event.triggerFactVersion());
    }

    private void require(SatisfactionTaskCreatedMessage event) {
        if (event == null || blank(event.eventId()) || event.tenantId() == null || event.projectId() == null
                || event.projectTaskId() == null || event.projectTaskVersion() == null
                || event.projectTaskVersion() < 0 || !TASK_CODE.equals(event.taskCode()) || event.taskId() == null
                || blank(event.collectionKey()) || event.taskRevisionNo() == null || event.taskRevisionNo() <= 0
                || blank(event.sourceOwnerContext()) || blank(event.sourceObjectType()) || blank(event.sourceObjectId())
                || event.sourceObjectVersion() == null || event.sourceObjectVersion() <= 0
                || blank(event.triggerOwnerContext()) || blank(event.triggerObjectType()) || blank(event.triggerFactId())
                || event.triggerFactVersion() == null || event.triggerFactVersion() <= 0
                || event.questionnaireId() == null || event.templateRevisionId() == null
                || event.templateVersion() == null || event.templateVersion() <= 0 || blank(event.ruleVersion())
                || event.threshold() == null || event.assigneeUserId() == null || event.assigneeUserId() <= 0) {
            throw new IllegalArgumentException("invalid satisfaction task event");
        }
    }

    private String digest(SatisfactionTaskCreatedMessage event) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(event).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record TodoProjectionResult(String key, Long taskId, Integer taskRevisionNo) {}
}
