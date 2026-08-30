package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SatisfactionTaskManagementService {
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionResultMapper resultMapper;
    private final SatisfactionRemediationFactMapper remediationMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final PlatformCommandExecutionApi commandExecutionApi;

    public List<TaskView> list(Long tenantId, Long actorUserId, Long projectId) {
        Set<Long> scope = projectId == null
                ? projectScopeApi.resolveAllCurrent(new ProjectAllScopeQuery(tenantId, actorUserId,
                ProjectScopeApi.ACTION_VIEW))
                : requireScope(tenantId, actorUserId, projectId, ProjectScopeApi.ACTION_VIEW);
        if (scope == null || scope.isEmpty()) return List.of();
        return taskMapper.selectByScope(new SatisfactionTaskScopeQuery(tenantId, scope, actorUserId)).stream()
                .map(this::view).toList();
    }

    public TaskView get(Long tenantId, Long actorUserId, Long taskId) {
        SatisfactionCollectionTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !tenantId.equals(task.getTenantId()) || !actorUserId.equals(task.getAssignedToUserId())) {
            throw new IllegalStateException("SATISFACTION_TASK_NOT_FOUND");
        }
        requireScope(tenantId, actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_VIEW);
        return view(task);
    }

    public AssignmentResult assign(Long tenantId, Long actorUserId, Long taskId, Long targetUserId,
                                   Integer expectedVersion, String operationId) {
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenantId,
                        "ACC_SATISFACTION_TASK_ASSIGN", actorUserId, operationId),
                digest(List.of(taskId, targetUserId, expectedVersion)), AssignmentResult.class,
                () -> assignOnce(tenantId, actorUserId, taskId, targetUserId, expectedVersion),
                result -> new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_TASK_ASSIGNED",
                        "SatisfactionCollectionTask", String.valueOf(taskId), operationId,
                        JsonUtils.toJsonString(result), List.of()));
        return requireExecution(execution, "SATISFACTION_TASK_ASSIGN");
    }

    @Transactional(rollbackFor = Exception.class)
    protected AssignmentResult assignOnce(Long tenantId, Long actorUserId, Long taskId, Long targetUserId,
                                          Integer expectedVersion) {
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(tenantId, taskId);
        if (task == null || expectedVersion == null || !expectedVersion.equals(task.getVersion())) {
            throw new IllegalStateException("SATISFACTION_TASK_VERSION_CONFLICT");
        }
        requireScope(tenantId, actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_EDIT);
        boolean member = memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(
                        tenantId, targetUserId, LocalDateTime.now())).stream()
                .map(ProjectMemberAssignmentDO::getProjectId).anyMatch(task.getProjectId()::equals);
        if (!member) throw new IllegalStateException("SATISFACTION_TASK_ASSIGNEE_NOT_AUTHORIZED");
        if (taskMapper.assign(new SatisfactionTaskAssignmentUpdate(tenantId, taskId, expectedVersion,
                targetUserId, actorUserId, String.valueOf(actorUserId))) != 1) {
            throw new IllegalStateException("SATISFACTION_TASK_VERSION_CONFLICT");
        }
        return new AssignmentResult(taskId, targetUserId, expectedVersion + 1, false);
    }

    public RecollectResult recollect(Long tenantId, Long actorUserId, Long taskId, Recollect command) {
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenantId,
                        "ACC_SATISFACTION_TASK_RECOLLECT", actorUserId, command.remediationRequestId()),
                digest(List.of(taskId, command.priorResultId(), command.remediationRequestId(),
                        command.evidenceSummary(), String.valueOf(command.evidenceFileFactVersion()))),
                RecollectResult.class, () -> recollectOnce(tenantId, actorUserId, taskId, command),
                result -> recollectFacts(tenantId, actorUserId, command, result));
        return requireExecution(execution, "SATISFACTION_TASK_RECOLLECT");
    }

    @Transactional(rollbackFor = Exception.class)
    protected RecollectResult recollectOnce(Long tenantId, Long actorUserId, Long taskId, Recollect command) {
        SatisfactionCollectionTaskDO prior = taskMapper.selectByIdForUpdate(tenantId, taskId);
        SatisfactionResultDO result = resultMapper.selectByIdForUpdate(tenantId, command.priorResultId());
        if (prior == null || result == null || !Objects.equals(prior.getResultId(), result.getId())
                || !Objects.equals(result.getCollectionTaskId(), prior.getId())
                || !(!Boolean.TRUE.equals(result.getPassed()) || "INVALIDATED".equals(result.getResultStatus()))) {
            throw new IllegalStateException("SATISFACTION_RECOLLECT_PRECONDITION_FAILED");
        }
        requireScope(tenantId, actorUserId, prior.getProjectId(), ProjectScopeApi.ACTION_EDIT);
        ProjectSatisfactionTaskFact projectTaskFact = workBindingFactApi.lockCurrentSatisfactionTask(
                new ProjectSatisfactionTaskIdentityQuery(prior.getProjectId(), prior.getProjectTaskId()));
        if (projectTaskFact == null || !Objects.equals(projectTaskFact.projectId(), prior.getProjectId())
                || !Objects.equals(projectTaskFact.projectTaskId(), prior.getProjectTaskId())
                || !"T-SAT-SURVEY".equals(projectTaskFact.taskCode())
                || projectTaskFact.projectTaskVersion() == null || projectTaskFact.projectTaskVersion() < 0) {
            throw new IllegalStateException("SATISFACTION_PROJECT_TASK_IDENTITY_CONFLICT");
        }
        SatisfactionRemediationIdentityQuery identity = new SatisfactionRemediationIdentityQuery(
                tenantId, result.getId(), command.remediationRequestId());
        SatisfactionRemediationFactDO existing = remediationMapper.selectByIdentity(identity);
        if (existing != null) {
            return replayRecollect(tenantId, prior, existing, command);
        }
        SatisfactionQuestionnaireDO previousQuestionnaire = questionnaireMapper.selectByIdForUpdate(
                tenantId, prior.getQuestionnaireId());
        if (previousQuestionnaire == null) throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_UNAVAILABLE");
        int revision = prior.getTaskRevisionNo() + 1;
        LocalDateTime now = LocalDateTime.now();
        SatisfactionRemediationFactDO remediation = new SatisfactionRemediationFactDO();
        remediation.setId(IdWorker.getId()); remediation.setTenantId(tenantId); remediation.setPriorResultId(result.getId());
        remediation.setRemediationRevisionNo(revision); remediation.setRemediationRequestId(command.remediationRequestId());
        remediation.setEvidenceSummary(command.evidenceSummary());
        remediation.setEvidenceFileFactVersion(command.evidenceFileFactVersion());
        remediation.setCompletedBy(actorUserId); remediation.setCompletedAt(now); remediation.setFactVersion(1L);
        remediation.setCreator(String.valueOf(actorUserId)); remediation.setCreateTime(now);
        if (remediationMapper.insert(remediation) != 1) throw new IllegalStateException("SATISFACTION_REMEDIATION_CONFLICT");

        Long newTaskId = IdWorker.getId(); Long questionnaireId = IdWorker.getId();
        SatisfactionCollectionTaskDO next = new SatisfactionCollectionTaskDO();
        next.setId(newTaskId); next.setTenantId(tenantId); next.setProjectId(prior.getProjectId());
        next.setProjectTaskId(prior.getProjectTaskId()); next.setSourceOwnerContext(prior.getSourceOwnerContext());
        next.setSourceObjectType(prior.getSourceObjectType()); next.setSourceObjectId(prior.getSourceObjectId());
        next.setSourceObjectVersion(prior.getSourceObjectVersion()); next.setTriggerOwnerContext("ACC");
        next.setTriggerObjectType("SatisfactionRemediationFact");
        next.setTriggerFactId(String.valueOf(remediation.getId())); next.setTriggerFactVersion(1L);
        next.setCollectionKey(prior.getCollectionKey()); next.setTaskRevisionNo(revision); next.setPriorTaskId(prior.getId());
        next.setAssignedToUserId(prior.getAssignedToUserId()); next.setAssignedByUserId(actorUserId);
        next.setTaskStatus("PENDING_COLLECTION"); next.setQuestionnaireId(questionnaireId); next.setVersion(0);
        next.setCreator(String.valueOf(actorUserId)); next.setUpdater(String.valueOf(actorUserId));

        SatisfactionQuestionnaireDO questionnaire = new SatisfactionQuestionnaireDO();
        questionnaire.setId(questionnaireId); questionnaire.setTenantId(tenantId); questionnaire.setCollectionTaskId(newTaskId);
        questionnaire.setTemplateId(previousQuestionnaire.getTemplateId());
        questionnaire.setTemplateRevisionId(previousQuestionnaire.getTemplateRevisionId());
        questionnaire.setTemplateVersion(previousQuestionnaire.getTemplateVersion());
        questionnaire.setFrozenQuestionJson(previousQuestionnaire.getFrozenQuestionJson());
        questionnaire.setFrozenThreshold(previousQuestionnaire.getFrozenThreshold());
        questionnaire.setRuleVersion(previousQuestionnaire.getRuleVersion()); questionnaire.setQuestionnaireStatus("ACTIVE");
        questionnaire.setAccessScopeVersion(previousQuestionnaire.getAccessScopeVersion()); questionnaire.setVersion(0);
        questionnaire.setCreator(String.valueOf(actorUserId)); questionnaire.setUpdater(String.valueOf(actorUserId));
        if (taskMapper.insert(next) != 1 || questionnaireMapper.insert(questionnaire) != 1) {
            throw new IllegalStateException("SATISFACTION_RECOLLECT_WRITE_CONFLICT");
        }
        return new RecollectResult(newTaskId, questionnaireId, prior.getCollectionKey(), revision,
                remediation.getId(), prior.getId(), projectTaskFact.projectTaskVersion(),
                projectTaskFact.taskCode(), false);
    }

    public void requireManageable(Long tenantId, Long actorUserId, Long taskId) {
        SatisfactionCollectionTaskDO task = taskMapper.selectById(taskId);
        if (task == null || !tenantId.equals(task.getTenantId())) throw new IllegalStateException("SATISFACTION_TASK_NOT_FOUND");
        requireScope(tenantId, actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_EDIT);
    }

    private RecollectResult replayRecollect(Long tenantId, SatisfactionCollectionTaskDO prior,
                                            SatisfactionRemediationFactDO remediation, Recollect command) {
        if (!Objects.equals(remediation.getEvidenceSummary(), command.evidenceSummary())
                || !Objects.equals(remediation.getEvidenceFileFactVersion(), command.evidenceFileFactVersion())) {
            throw new IllegalStateException("SATISFACTION_REMEDIATION_IDEMPOTENCY_CONFLICT");
        }
        SatisfactionCollectionTaskDO next = taskMapper.selectByScope(new SatisfactionTaskScopeQuery(
                        tenantId, Set.of(prior.getProjectId()), null)).stream()
                .filter(row -> Objects.equals(row.getPriorTaskId(), prior.getId())
                        && Objects.equals(row.getTriggerFactId(), String.valueOf(remediation.getId())))
                .findFirst().orElseThrow(() -> new IllegalStateException("SATISFACTION_REMEDIATION_REPLAY_INCOMPLETE"));
        return new RecollectResult(next.getId(), next.getQuestionnaireId(), next.getCollectionKey(),
                next.getTaskRevisionNo(), remediation.getId(), prior.getId(), null, null, true);
    }

    private PlatformCommandExecutionApi.SuccessFacts recollectFacts(Long tenantId, Long actorUserId, Recollect command,
                                                                     RecollectResult result) {
        SatisfactionCollectionTaskDO task = taskMapper.selectById(result.taskId());
        SatisfactionQuestionnaireDO questionnaire = questionnaireMapper.selectById(result.questionnaireId());
        Map<String, Object> payload = new LinkedHashMap<>();
        String eventId = command.remediationRequestId() + ":satisfaction-task-created";
        payload.put("eventId", eventId); payload.put("tenantId", tenantId); payload.put("projectId", task.getProjectId());
        payload.put("projectTaskId", task.getProjectTaskId()); payload.put("taskId", task.getId());
        payload.put("projectTaskVersion", result.projectTaskVersion()); payload.put("taskCode", result.taskCode());
        payload.put("collectionKey", task.getCollectionKey()); payload.put("taskRevisionNo", task.getTaskRevisionNo());
        payload.put("priorTaskId", task.getPriorTaskId()); payload.put("sourceOwnerContext", task.getSourceOwnerContext());
        payload.put("sourceObjectType", task.getSourceObjectType()); payload.put("sourceObjectId", task.getSourceObjectId());
        payload.put("sourceObjectVersion", task.getSourceObjectVersion()); payload.put("triggerOwnerContext", "ACC");
        payload.put("triggerObjectType", "SatisfactionRemediationFact"); payload.put("triggerFactId", task.getTriggerFactId());
        payload.put("triggerFactVersion", 1); payload.put("questionnaireId", questionnaire.getId());
        payload.put("templateRevisionId", questionnaire.getTemplateRevisionId());
        payload.put("templateVersion", questionnaire.getTemplateVersion()); payload.put("ruleVersion", questionnaire.getRuleVersion());
        payload.put("threshold", questionnaire.getFrozenThreshold()); payload.put("assigneeUserId", task.getAssignedToUserId());
        return new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_TASK_RECOLLECTED",
                "SatisfactionCollectionTask", String.valueOf(result.taskId()), command.remediationRequestId(),
                JsonUtils.toJsonString(result), List.of(new PlatformCommandExecutionApi.BusinessEvent(
                eventId, "SatisfactionTaskCreated", JsonUtils.toJsonString(payload))));
    }

    private Set<Long> requireScope(Long tenantId, Long actorUserId, Long projectId, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorUserId, projectId, action));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw new IllegalStateException("SATISFACTION_PROJECT_SCOPE_FORBIDDEN");
        }
        return scope.fullProjectIds();
    }

    private TaskView view(SatisfactionCollectionTaskDO row) {
        SatisfactionQuestionnaireDO questionnaire = row.getQuestionnaireId() == null ? null
                : questionnaireMapper.selectById(row.getQuestionnaireId());
        return new TaskView(row.getId(), row.getProjectId(), row.getProjectTaskId(), row.getCollectionKey(),
                row.getTaskRevisionNo(), row.getPriorTaskId(), row.getAssignedToUserId(), row.getAssignedByUserId(),
                row.getTaskStatus(), row.getQuestionnaireId(), row.getResultId(), row.getVersion(),
                questionnaire == null ? null : questionnaire.getQuestionnaireStatus(),
                questionnaire == null ? null : questionnaire.getTemplateRevisionId());
    }

    private <T> T requireExecution(PlatformCommandExecutionApi.ExecutionResult<T> execution, String code) {
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw new IllegalStateException(code + "_IDEMPOTENCY_CONFLICT");
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) throw new IllegalStateException(code + "_IN_PROGRESS");
        return execution.response();
    }

    private String digest(Object value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record TaskView(Long id, Long projectId, Long projectTaskId, String collectionKey, Integer revisionNo,
                           Long priorTaskId, Long assignedToUserId, Long assignedByUserId, String status,
                           Long questionnaireId, Long resultId, Integer version, String questionnaireStatus,
                           Long templateRevisionId) {}
    public record AssignmentResult(Long taskId, Long assignedToUserId, Integer version, boolean replayed) {}
    public record Recollect(Long priorResultId, String remediationRequestId, String evidenceSummary,
                            String evidenceFileFactVersion) {}
    public record RecollectResult(Long taskId, Long questionnaireId, String collectionKey, Integer revisionNo,
                                  Long remediationFactId, Long priorTaskId, Integer projectTaskVersion,
                                  String taskCode, boolean replayed) {}
}
