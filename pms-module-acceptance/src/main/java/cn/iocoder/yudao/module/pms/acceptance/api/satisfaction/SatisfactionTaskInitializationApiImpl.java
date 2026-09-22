package cn.iocoder.yudao.module.pms.acceptance.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTaskInitializationCommand;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTaskInitializationResult;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskProjectQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionFirstTaskQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFactQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionTaskTriggerLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionTemplateRevisionQuery;
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
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;

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
    private final cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper deliverableMapper;

    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectManualSatisfactionApi manualProjects;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public SatisfactionTaskInitializationResult initialize(SatisfactionTaskInitializationCommand command) {
        return initialize(command, false, null);
    }

    /** 管理端首轮发起；来源和冻结配置由服务端生成，不接受客户端提供业务完成事实。 */
    @Transactional(rollbackFor = Exception.class)
    public SatisfactionTaskInitializationResult startManual(Long projectId, Long actorUserId, String operationId) {
        return startManual(projectId, actorUserId, operationId, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public SatisfactionTaskInitializationResult startManual(Long projectId, Long actorUserId, String operationId,
                                                            Long projectTaskId, Long templateId, Long revisionId) {
        Long tenantId = trustedTenantId();
        if (!positive(projectId) || !positive(actorUserId) || blank(operationId)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorUserId, projectId, ProjectScopeApi.ACTION_EDIT));
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        var fact = projectTaskId == null && templateId == null && revisionId == null
                ? workBindingFactApi.lockCurrentSatisfactionTaskByProject(new ProjectSatisfactionTaskProjectQuery(projectId))
                : manualProjects.freeze(new cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectManualSatisfactionApi.Selection(
                        projectId, projectTaskId, templateId, revisionId, actorUserId));
        var lockedScope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, actorUserId, projectId, ProjectScopeApi.ACTION_EDIT, scope.treeVersion()));
        if (lockedScope == null || !Objects.equals(scope.treeVersion(), lockedScope.treeVersion())
                || lockedScope.fullProjectIds() == null || !lockedScope.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        var command = new SatisfactionTaskInitializationCommand(tenantId, projectId, fact.projectTaskId(),
                fact.projectTaskVersion(), "ACC", "SatisfactionManualInitiation", String.valueOf(projectId),
                1L, "ACC", "SatisfactionManualInitiation", String.valueOf(projectId), 1L, operationId);
        var result = initialize(command, true, actorUserId);
        if (!"CREATED".equals(result.outcome()) && !"REPLAYED".equals(result.outcome())) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return result;
    }

    private SatisfactionTaskInitializationResult initialize(SatisfactionTaskInitializationCommand command,
                                                            boolean manual, Long manualActorId) {
        Long tenantId = trustedTenantId();
        if (!valid(command, tenantId)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }

        ProjectSatisfactionTaskFact taskFact = workBindingFactApi.lockAndRevalidateSatisfactionTask(
                new ProjectSatisfactionTaskFactQuery(command.projectId(), command.projectTaskId(),
                        command.expectedProjectTaskVersion()));
        if (!Objects.equals(taskFact.projectId(), command.projectId())
                || !Objects.equals(taskFact.projectTaskId(), command.projectTaskId())) {
            return conflict();
        }
        if (!manual && !APPLICABLE_TIMING.equals(taskFact.satisfactionTiming())) {
            var first = taskMapper.selectFirstByProjectForUpdate(new SatisfactionFirstTaskQuery(tenantId, command.projectId()));
            if (first == null || !Objects.equals(first.getProjectTaskId(), command.projectTaskId())) return conflict();
            return new SatisfactionTaskInitializationResult("REPLAYED", first.getId(), first.getQuestionnaireId(),
                    first.getCollectionKey(), first.getTaskRevisionNo(), first.getVersion());
        }

        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        tenantId, "ACC_SATISFACTION_TASK_INITIALIZATION",
                        manual ? manualActorId : taskFact.currentAssigneeUserId(), command.operationId()),
                digest(manual ? List.of("MANUAL", command.tenantId(), command.projectId()) : command),
                SatisfactionTaskInitializationResult.class,
                () -> initializeOnce(command, taskFact, tenantId, manualActorId),
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
                                                                  Long tenantId, Long manualActorId) {
        SatisfactionTaskTriggerLockQuery triggerQuery = new SatisfactionTaskTriggerLockQuery(tenantId,
                command.projectTaskId(), command.triggerOwnerContext(), command.triggerObjectType(),
                command.triggerFactId(), command.triggerFactVersion());
        SatisfactionCollectionTaskDO existing = taskMapper.selectByTriggerForUpdate(triggerQuery);
        if (existing != null) {
            return replayOrConflict(existing, command);
        }

        // PROJ事实锁串行化手动/自动首轮创建；后续自动触发不能创建第二条收集链。
        var first = taskMapper.selectFirstByProjectForUpdate(new SatisfactionFirstTaskQuery(tenantId, command.projectId()));
        if (first != null) {
            if (!Objects.equals(first.getProjectTaskId(), command.projectTaskId())) return conflict();
            return new SatisfactionTaskInitializationResult("REPLAYED", first.getId(), first.getQuestionnaireId(),
                    first.getCollectionKey(), first.getTaskRevisionNo(), first.getVersion());
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

        var deliverables = deliverableMapper.selectTaskDeliverablesForUpdate(
                new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper.TaskDeliverablesQuery(
                        tenantId, command.projectId(), taskFact.taskCode()));
        if (deliverables.isEmpty() && "SatisfactionManualInitiation".equals(command.sourceObjectType())) {
            var option = manualProjects.options(command.projectId(), manualActorId).tasks().stream()
                    .filter(task -> task.id().equals(taskFact.projectTaskId())).findFirst().orElseThrow();
            var report = new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO();
            report.setId(IdWorker.getId()); report.setTenantId(tenantId); report.setProjectId(command.projectId());
            report.setDeliverableCode("D-SAT-MANUAL-" + taskFact.projectTaskId()); report.setName("满意度调查报告");
            report.setStageCode(option.stageCode()); report.setTaskCode(taskFact.taskCode());
            report.setRequired(false); report.setStatus("PENDING"); report.setArchiveStatus("PENDING"); report.setVersion(0);
            if (deliverableMapper.insert(report) != 1) throw new IllegalStateException("SATISFACTION_DELIVERABLE_CREATE_FAILED");
            deliverables = List.of(report);
        }
        if (deliverables.size() != 1) throw new IllegalStateException("SATISFACTION_DELIVERABLE_BINDING_NOT_UNIQUE");
        var deliverable = deliverables.getFirst();
        if (!Objects.equals(deliverable.getTenantId(), tenantId) || !Objects.equals(deliverable.getProjectId(), command.projectId())
                || !Objects.equals(deliverable.getTaskCode(), taskFact.taskCode()))
            throw new IllegalStateException("SATISFACTION_DELIVERABLE_BINDING_CONFLICT");
        long taskId = IdWorker.getId();
        long questionnaireId = IdWorker.getId();
        SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setProjectId(command.projectId());
        task.setProjectTaskId(command.projectTaskId());
        task.setDeliverableId(deliverable.getId());
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
        if ("REPLAYED".equals(result.outcome())) {
            return new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_TASK_INITIALIZATION_REPLAYED",
                    "SatisfactionCollectionTask", String.valueOf(result.taskId()), command.operationId(),
                    JsonUtils.toJsonString(result), List.of());
        }
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

    private String digest(Object command) {
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
