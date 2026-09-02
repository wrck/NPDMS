package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTaskInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTaskInitializationResult;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTaskTriggerLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTemplateRevisionQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;

@Service
@RequiredArgsConstructor
public class SatisfactionTaskInitializationApiImpl implements SatisfactionTaskInitializationApi {

    private static final String APPLICABLE_TIMING = "AFTER_INITIAL_ACCEPTANCE";

    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final ProjectScopeApi projectScopeApi;
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionQuestionnaireTemplateRevisionMapper templateRevisionMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public SatisfactionTaskInitializationResult initialize(SatisfactionTaskInitializationCommand command) {
        Long tenantId = trustedTenantId();
        if (!valid(command, tenantId)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }

        ProjectSatisfactionTaskFact taskFact = workBindingFactApi.lockAndRevalidateSatisfactionTask(
                new ProjectSatisfactionTaskFactQuery(command.projectId(), command.projectTaskId(),
                        command.expectedProjectTaskVersion()));
        if (!Objects.equals(taskFact.projectId(), command.projectId())
                || !Objects.equals(taskFact.projectTaskId(), command.projectTaskId())
                || !APPLICABLE_TIMING.equals(taskFact.satisfactionTiming())) {
            return conflict();
        }

        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        tenantId, "ACC_SATISFACTION_TASK_INITIALIZATION",
                        taskFact.currentAssigneeUserId(), command.operationId()),
                digest(command), SatisfactionTaskInitializationResult.class,
                () -> initializeOnce(command, taskFact, tenantId),
                result -> successFacts(command, taskFact, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) {
            return conflict();
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED) {
            SatisfactionTaskInitializationResult response = execution.response();
            return new SatisfactionTaskInitializationResult("REPLAYED", response.taskId(),
                    response.questionnaireId(), response.collectionKey(), response.taskRevisionNo(),
                    response.taskVersion());
        }
        return execution.response();
    }

    private SatisfactionTaskInitializationResult initializeOnce(SatisfactionTaskInitializationCommand command,
                                                                  ProjectSatisfactionTaskFact taskFact,
                                                                  Long tenantId) {
        SatisfactionTaskTriggerLockQuery triggerQuery = new SatisfactionTaskTriggerLockQuery(tenantId,
                command.projectTaskId(), command.triggerOwnerContext(), command.triggerObjectType(),
                command.triggerFactId(), command.triggerFactVersion());
        SatisfactionCollectionTaskDO existing = taskMapper.selectByTriggerForUpdate(triggerQuery);
        if (existing != null) {
            return replayOrConflict(existing, command);
        }

        ProjectScopeResult scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(tenantId,
                taskFact.currentAssigneeUserId(), command.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.treeVersion() == null || scope.treeVersion() < 0
                || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(command.projectId())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }

        SatisfactionQuestionnaireTemplateRevisionDO revision = templateRevisionMapper.selectFrozenRevision(
                new SatisfactionTemplateRevisionQuery(tenantId, taskFact.templateId(), taskFact.templateRevisionId()));
        if (!validRevision(revision, taskFact)) {
            return conflict();
        }

        long taskId = IdWorker.getId();
        long questionnaireId = IdWorker.getId();
        SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setProjectId(command.projectId());
        task.setProjectTaskId(command.projectTaskId());
        task.setSourceOwnerContext(command.sourceOwnerContext());
        task.setSourceObjectType(command.sourceObjectType());
        task.setSourceObjectId(command.sourceObjectId());
        task.setSourceObjectVersion(command.sourceObjectVersion());
        task.setTriggerOwnerContext(command.triggerOwnerContext());
        task.setTriggerObjectType(command.triggerObjectType());
        task.setTriggerFactId(command.triggerFactId());
        task.setTriggerFactVersion(command.triggerFactVersion());
        task.setCollectionKey("SAT-" + taskId);
        task.setTaskRevisionNo(1);
        task.setAssignedToUserId(taskFact.currentAssigneeUserId());
        task.setTaskStatus("PENDING_COLLECTION");
        task.setQuestionnaireId(questionnaireId);
        task.setVersion(0);

        SatisfactionQuestionnaireDO questionnaire = new SatisfactionQuestionnaireDO();
        questionnaire.setId(questionnaireId);
        questionnaire.setTenantId(tenantId);
        questionnaire.setCollectionTaskId(taskId);
        questionnaire.setTemplateId(taskFact.templateId());
        questionnaire.setTemplateRevisionId(taskFact.templateRevisionId());
        questionnaire.setTemplateVersion(taskFact.templateVersion());
        questionnaire.setFrozenQuestionJson(revision.getFrozenQuestionJson());
        questionnaire.setFrozenThreshold(taskFact.threshold());
        questionnaire.setRuleVersion(taskFact.ruleVersion());
        questionnaire.setQuestionnaireStatus("ACTIVE");
        questionnaire.setAccessScopeVersion(scope.treeVersion());
        questionnaire.setVersion(0);

        taskMapper.insert(task);
        questionnaireMapper.insert(questionnaire);
        return new SatisfactionTaskInitializationResult("CREATED", taskId, questionnaireId,
                task.getCollectionKey(), 1, 0);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(SatisfactionTaskInitializationCommand command,
                                                                   ProjectSatisfactionTaskFact taskFact,
                                                                   SatisfactionTaskInitializationResult result) {
        if (result == null || "FACT_CONFLICT".equals(result.outcome())) {
            return new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_TASK_INITIALIZATION_CONFLICT",
                    "SatisfactionCollectionTask", String.valueOf(command.projectTaskId()), command.operationId(),
                    "{}", List.of());
        }
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId); payload.put("tenantId", command.tenantId());
        payload.put("projectId", command.projectId()); payload.put("projectTaskId", command.projectTaskId());
        payload.put("projectTaskVersion", taskFact.projectTaskVersion()); payload.put("taskCode", taskFact.taskCode());
        payload.put("taskId", result.taskId()); payload.put("collectionKey", result.collectionKey());
        payload.put("taskRevisionNo", result.taskRevisionNo()); payload.put("priorTaskId", null);
        payload.put("sourceOwnerContext", command.sourceOwnerContext());
        payload.put("sourceObjectType", command.sourceObjectType()); payload.put("sourceObjectId", command.sourceObjectId());
        payload.put("sourceObjectVersion", command.sourceObjectVersion());
        payload.put("triggerOwnerContext", command.triggerOwnerContext());
        payload.put("triggerObjectType", command.triggerObjectType()); payload.put("triggerFactId", command.triggerFactId());
        payload.put("triggerFactVersion", command.triggerFactVersion());
        payload.put("questionnaireId", result.questionnaireId());
        payload.put("templateRevisionId", taskFact.templateRevisionId());
        payload.put("templateVersion", taskFact.templateVersion()); payload.put("ruleVersion", taskFact.ruleVersion());
        payload.put("threshold", taskFact.threshold()); payload.put("assigneeUserId", taskFact.currentAssigneeUserId());
        return new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_TASK_INITIALIZED",
                "SatisfactionCollectionTask", String.valueOf(result.taskId()), command.operationId(),
                JsonUtils.toJsonString(Map.of("taskId", result.taskId(), "questionnaireId", result.questionnaireId())),
                List.of(new PlatformCommandExecutionApi.BusinessEvent(eventId, "SatisfactionTaskCreated",
                        JsonUtils.toJsonString(payload))));
    }

    private String digest(SatisfactionTaskInitializationCommand command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(command).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private SatisfactionTaskInitializationResult replayOrConflict(SatisfactionCollectionTaskDO task,
                                                                   SatisfactionTaskInitializationCommand command) {
        if (!Objects.equals(task.getProjectId(), command.projectId())
                || !Objects.equals(task.getProjectTaskId(), command.projectTaskId())
                || !Objects.equals(task.getSourceOwnerContext(), command.sourceOwnerContext())
                || !Objects.equals(task.getSourceObjectType(), command.sourceObjectType())
                || !Objects.equals(task.getSourceObjectId(), command.sourceObjectId())
                || !Objects.equals(task.getSourceObjectVersion(), command.sourceObjectVersion())
                || task.getQuestionnaireId() == null || task.getTaskRevisionNo() == null) {
            return conflict();
        }
        return new SatisfactionTaskInitializationResult("REPLAYED", task.getId(), task.getQuestionnaireId(),
                task.getCollectionKey(), task.getTaskRevisionNo(), task.getVersion());
    }

    private boolean validRevision(SatisfactionQuestionnaireTemplateRevisionDO revision,
                                  ProjectSatisfactionTaskFact taskFact) {
        return revision != null && Objects.equals(revision.getTemplateId(), taskFact.templateId())
                && Objects.equals(revision.getId(), taskFact.templateRevisionId())
                && Objects.equals(revision.getRevisionNo(), taskFact.templateVersion())
                && Objects.equals(revision.getRuleVersion(), taskFact.ruleVersion())
                && revision.getFrozenThreshold() != null
                && revision.getFrozenThreshold().compareTo(taskFact.threshold()) == 0
                && !blank(revision.getFrozenQuestionJson());
    }

    private boolean valid(SatisfactionTaskInitializationCommand command, Long tenantId) {
        return command != null && Objects.equals(command.tenantId(), tenantId)
                && positive(command.projectId()) && positive(command.projectTaskId())
                && command.expectedProjectTaskVersion() != null && command.expectedProjectTaskVersion() >= 0
                && !blank(command.sourceOwnerContext()) && !blank(command.sourceObjectType())
                && !blank(command.sourceObjectId()) && positive(command.sourceObjectVersion())
                && !blank(command.triggerOwnerContext()) && !blank(command.triggerObjectType())
                && !blank(command.triggerFactId()) && positive(command.triggerFactVersion())
                && !blank(command.operationId());
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private SatisfactionTaskInitializationResult conflict() {
        return new SatisfactionTaskInitializationResult("FACT_CONFLICT", null, null, null, null, null);
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return tenantId;
    }
}
