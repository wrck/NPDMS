package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFileCommand;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionTaskResultUpdate;
import cn.iocoder.yudao.module.pms.project.domain.satisfaction.SatisfactionQuestionnaireDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SatisfactionResultDecisionService {
    private static final String TASK_CODE = "T-SAT-SURVEY";
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionResponseMapper responseMapper;
    private final SatisfactionResponseFileMapper responseFileMapper;
    private final SatisfactionResultMapper resultMapper;
    private final SatisfactionResultFileMapper resultFileMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final FileArtifactApi fileArtifactApi;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Transactional(rollbackFor = Exception.class)
    public DecisionResult decide(Command command) {
        require(command);
        SatisfactionCollectionTaskDO current = taskMapper.selectById(command.taskId());
        if (current == null || !command.tenantId().equals(current.getTenantId())
                || current.getAssignedToUserId() == null) {
            throw new IllegalStateException("SATISFACTION_RESULT_TASK_CONFLICT");
        }
        Long actorUserId = current.getAssignedToUserId();
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "ACC_SATISFACTION_RESULT_DECISION", actorUserId, command.operationId()),
                digest(command), DecisionResult.class, () -> decideOnce(command, actorUserId), this::successFacts);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IllegalStateException("SATISFACTION_RESULT_IDEMPOTENCY_CONFLICT");
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) {
            throw new IllegalStateException("SATISFACTION_RESULT_IN_PROGRESS");
        }
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? execution.response().withReplay() : execution.response();
    }

    private DecisionResult decideOnce(Command command, Long actorUserId) {
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(command.tenantId(), command.taskId());
        if (task == null || !"PENDING_DECISION".equals(task.getTaskStatus()) || task.getResultId() != null
                || !command.questionnaireId().equals(task.getQuestionnaireId())
                || !actorUserId.equals(task.getAssignedToUserId())) {
            throw new IllegalStateException("SATISFACTION_RESULT_TASK_CONFLICT");
        }
        SatisfactionQuestionnaireDO questionnaire = questionnaireMapper.selectByIdForUpdate(
                command.tenantId(), command.questionnaireId());
        SatisfactionResponseDO response = responseMapper.selectByIdForUpdate(command.tenantId(), command.responseId());
        if (questionnaire == null || response == null || !task.getId().equals(questionnaire.getCollectionTaskId())
                || !questionnaire.getId().equals(response.getQuestionnaireId())) {
            throw new IllegalStateException("SATISFACTION_RESULT_OWNER_CONFLICT");
        }
        var evaluation = SatisfactionQuestionnaireDefinition.parse(questionnaire.getFrozenQuestionJson())
                .evaluate(response.getAnswerSnapshot(), true);
        if (questionnaire.getFrozenThreshold() == null || questionnaire.getRuleVersion() == null
                || questionnaire.getFrozenThreshold().compareTo(evaluation.threshold()) != 0
                || !questionnaire.getRuleVersion().equals(evaluation.ruleVersion())) {
            throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_PROJECTION_CONFLICT");
        }
        var scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(command.tenantId(),
                actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_EDIT,
                questionnaire.getAccessScopeVersion()));
        if (scope == null || !questionnaire.getAccessScopeVersion().equals(scope.treeVersion())
                || !scope.fullProjectIds().contains(task.getProjectId())) {
            throw new IllegalStateException("SATISFACTION_RESULT_SCOPE_CONFLICT");
        }
        ProjectSatisfactionTaskFact projectTask = workBindingFactApi.lockCurrentSatisfactionTask(
                new ProjectSatisfactionTaskIdentityQuery(task.getProjectId(), task.getProjectTaskId()));
        if (projectTask == null || !task.getProjectId().equals(projectTask.projectId())
                || !task.getProjectTaskId().equals(projectTask.projectTaskId())
                || !TASK_CODE.equals(projectTask.taskCode()) || projectTask.projectTaskVersion() == null) {
            throw new IllegalStateException("SATISFACTION_RESULT_PROJECT_TASK_CONFLICT");
        }

        Long resultId = response.getId();
        if (resultMapper.selectByIdForUpdate(command.tenantId(), resultId) != null) {
            throw new IllegalStateException("SATISFACTION_RESULT_ID_CONFLICT");
        }
        byte[] content = SatisfactionResultDocumentRenderer.render(resultId, task.getId(), questionnaire.getId(),
                response.getId(), evaluation.score().toPlainString(), evaluation.threshold().toPlainString(),
                evaluation.passed(), evaluation.ruleVersion());
        FileArtifactVersionFact file = fileArtifactApi.createGeneratedBusinessFile(new GeneratedBusinessFileCommand(
                command.tenantId(), actorUserId, command.operationId(), resultId, task.getId(),
                questionnaire.getId(), response.getId(), task.getVersion(), "ACC", "SATISFACTION_RESULT",
                "SATISFACTION_RESULT_DOCUMENT", scope.treeVersion(), "satisfaction-result-" + resultId + ".pdf",
                "application/pdf", content));
        requireFile(file, scope.treeVersion());

        LocalDateTime now = LocalDateTime.now();
        SatisfactionResultDO result = result(task, questionnaire, response, resultId, evaluation, now, command,
                actorUserId);
        List<DecisionFile> files = resultFiles(resultId, response.getId(), file, now, actorUserId,
                command.tenantId(), scope.treeVersion());
        if (resultMapper.insert(result) != 1
                || files.stream().map(DecisionFile::row).anyMatch(row -> resultFileMapper.insert(row) != 1)
                || taskMapper.completeDecision(new SatisfactionTaskResultUpdate(command.tenantId(), task.getId(),
                task.getVersion(), resultId, evaluation.passed() ? "PENDING_ARCHIVE" : "FAILED",
                String.valueOf(actorUserId))) != 1) {
            throw new IllegalStateException("SATISFACTION_RESULT_WRITE_CONFLICT");
        }
        return new DecisionResult(command.operationId(), command.tenantId(), task.getProjectId(), task.getProjectTaskId(),
                projectTask.projectTaskVersion(), task.getId(), task.getTaskRevisionNo(), questionnaire.getId(),
                questionnaire.getTemplateRevisionId(),
                response.getId(), resultId, 1, result.getVersion(), task.getCollectionKey(), task.getSourceOwnerContext(),
                task.getSourceObjectType(), task.getSourceObjectId(), task.getSourceObjectVersion(),
                evaluation.score(), evaluation.threshold(), evaluation.passed(), evaluation.ruleVersion(),
                result.getResultStatus(), actorUserId, files.stream()
                        .map(item -> new ResultFileFact(item.role(), item.sequence(), item.fact())).toList(), false);
    }

    private SatisfactionResultDO result(SatisfactionCollectionTaskDO task, SatisfactionQuestionnaireDO questionnaire,
                                        SatisfactionResponseDO response, Long resultId,
                                        SatisfactionQuestionnaireDefinition.Evaluation evaluation,
                                        LocalDateTime now, Command command, Long actorUserId) {
        SatisfactionResultDO row = new SatisfactionResultDO();
        row.setId(resultId); row.setTenantId(command.tenantId()); row.setCollectionTaskId(task.getId());
        row.setQuestionnaireId(questionnaire.getId()); row.setResponseId(response.getId());
        row.setCollectionKey(task.getCollectionKey()); row.setResultVersion(1); row.setScore(evaluation.score());
        row.setThreshold(evaluation.threshold()); row.setPassed(evaluation.passed());
        row.setRuleVersion(evaluation.ruleVersion()); row.setResultStatus(evaluation.passed() ? "EFFECTIVE" : "FAILED");
        row.setEffectiveFrom(now); row.setArchiveStatus("PENDING_COMPENSATION");
        row.setArchiveActorUserId(actorUserId); row.setArchiveRetryCount(0); row.setVersion(0);
        row.setCreator(String.valueOf(actorUserId)); row.setUpdater(String.valueOf(actorUserId));
        row.setCreateTime(now); row.setUpdateTime(now);
        return row;
    }

    private SatisfactionResultFileDO resultFile(Long resultId, FileArtifactVersionFact file, LocalDateTime now,
                                                Long actorUserId, Long tenantId) {
        SatisfactionResultFileDO row = new SatisfactionResultFileDO();
        row.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId()); row.setTenantId(tenantId);
        row.setResultId(resultId); row.setFileRole("RESULT_DOCUMENT"); row.setFileSequence(1);
        row.setArtifactId(file.artifactId()); row.setVersionNo(file.versionNo()); row.setReferenceKey(file.referenceKey());
        row.setArtifactVersion(file.fileFactVersion().artifactVersion());
        row.setReferenceVersion(file.fileFactVersion().referenceVersion());
        row.setAvailabilityVersion(file.fileFactVersion().availabilityVersion());
        row.setScopeVersion(file.scopeVersion()); row.setFileHash(file.sha256());
        row.setCreator(String.valueOf(actorUserId)); row.setCreateTime(now);
        return row;
    }

    private List<DecisionFile> resultFiles(Long resultId, Long responseId, FileArtifactVersionFact document,
                                           LocalDateTime now, Long actorUserId, Long tenantId, Long scopeVersion) {
        List<DecisionFile> files = new java.util.ArrayList<>();
        SatisfactionResultFileDO documentRow = resultFile(resultId, document, now, actorUserId, tenantId);
        files.add(new DecisionFile("RESULT_DOCUMENT", 1, document, documentRow));
        List<SatisfactionResponseFileDO> responseFiles = responseFileMapper.selectListByResponse(
                new cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResponseFilesQuery(
                        tenantId, responseId));
        for (SatisfactionResponseFileDO source : responseFiles) {
            if (!List.of("SIGNATURE", "ATTACHMENT").contains(source.getFileRole())
                    || !scopeVersion.equals(source.getScopeVersion())) {
                throw new IllegalStateException("SATISFACTION_RESULT_RESPONSE_FILE_CONFLICT");
            }
            FileArtifactVersionFact fact = new FileArtifactVersionFact(source.getArtifactId(), source.getVersionNo(),
                    source.getReferenceKey(), null, null, null, null, source.getFileHash(), "AVAILABLE", "ACTIVE",
                    new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion(
                            source.getArtifactVersion(), source.getReferenceVersion(), source.getAvailabilityVersion()),
                    source.getScopeVersion());
            SatisfactionResultFileDO row = resultFile(resultId, fact, now, actorUserId, tenantId);
            row.setFileRole(source.getFileRole()); row.setFileSequence(source.getFileSequence());
            files.add(new DecisionFile(source.getFileRole(), source.getFileSequence(), fact, row));
        }
        if (files.stream().noneMatch(item -> "SIGNATURE".equals(item.role()))) {
            throw new IllegalStateException("SATISFACTION_RESULT_SIGNATURE_MISSING");
        }
        return List.copyOf(files);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(DecisionResult result) {
        String eventId = result.operationId() + ":result-recorded";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId); payload.put("changeType", "RECORDED");
        payload.put("tenantId", result.tenantId());
        payload.put("projectId", result.projectId()); payload.put("projectTaskId", result.projectTaskId());
        payload.put("projectTaskVersion", result.projectTaskVersion());
        payload.put("taskCode", TASK_CODE); payload.put("collectionKey", result.collectionKey());
        payload.put("taskRevisionNo", result.taskRevisionNo()); payload.put("taskId", result.taskId());
        payload.put("questionnaireId", result.questionnaireId()); payload.put("responseId", result.responseId());
        payload.put("resultId", result.resultId()); payload.put("resultVersion", result.resultVersion());
        payload.put("resultFactVersion", result.resultFactVersion());
        payload.put("templateRevisionId", result.templateRevisionId()); payload.put("ruleVersion", result.ruleVersion());
        payload.put("threshold", result.threshold()); payload.put("sourceOwnerContext", result.sourceOwnerContext());
        payload.put("sourceObjectType", result.sourceObjectType()); payload.put("sourceObjectId", result.sourceObjectId());
        payload.put("sourceObjectVersion", result.sourceObjectVersion()); payload.put("passed", result.passed());
        payload.put("resultStatus", result.resultStatus()); payload.put("archiveActorUserId", result.archiveActorUserId());
        payload.put("files", result.files().stream().map(this::filePayload).toList());
        return new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_RESULT_RECORDED", "SatisfactionResult",
                String.valueOf(result.resultId()), result.operationId(), JsonUtils.toJsonString(Map.of(
                "resultId", result.resultId(), "passed", result.passed())), List.of(
                new PlatformCommandExecutionApi.BusinessEvent(eventId, "SatisfactionResultVersionChanged",
                        JsonUtils.toJsonString(payload))));
    }

    private Map<String, Object> filePayload(ResultFileFact resultFile) {
        FileArtifactVersionFact file = resultFile.file();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("role", resultFile.role()); value.put("sequence", resultFile.sequence());
        value.put("artifactId", file.artifactId()); value.put("versionNo", file.versionNo());
        value.put("referenceKey", file.referenceKey());
        value.put("artifactVersion", file.fileFactVersion().artifactVersion());
        value.put("referenceVersion", file.fileFactVersion().referenceVersion());
        value.put("availabilityVersion", file.fileFactVersion().availabilityVersion());
        value.put("scopeVersion", file.scopeVersion()); value.put("sha256", file.sha256());
        return value;
    }

    private void requireFile(FileArtifactVersionFact file, Long scopeVersion) {
        if (file == null || file.artifactId() == null || file.versionNo() == null
                || file.referenceKey() == null || file.fileFactVersion() == null
                || !scopeVersion.equals(file.scopeVersion()) || file.sha256() == null
                || !file.sha256().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("SATISFACTION_RESULT_FILE_CONFLICT");
        }
    }

    private String digest(Command command) {
        return sha256(JsonUtils.toJsonString(Map.of("tenantId", command.tenantId(), "taskId", command.taskId(),
                "questionnaireId", command.questionnaireId(), "responseId", command.responseId(),
                "operationId", command.operationId())));
    }

    private void require(Command command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0 || command.taskId() == null
                || command.questionnaireId() == null || command.responseId() == null
                || command.operationId() == null || command.operationId().isBlank()) {
            throw new IllegalArgumentException("SATISFACTION_RESULT_COMMAND_INVALID");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Command(Long tenantId, Long taskId, Long questionnaireId, Long responseId, String operationId) {
    }

    public record DecisionResult(String operationId, Long tenantId, Long projectId, Long projectTaskId,
                                 Integer projectTaskVersion, Long taskId, Integer taskRevisionNo,
                                 Long questionnaireId, Long templateRevisionId,
                                 Long responseId, Long resultId, Integer resultVersion, Integer resultFactVersion,
                                 String collectionKey,
                                 String sourceOwnerContext, String sourceObjectType, String sourceObjectId,
                                 Long sourceObjectVersion, java.math.BigDecimal score, java.math.BigDecimal threshold,
                                 boolean passed, String ruleVersion, String resultStatus, Long archiveActorUserId,
                                 List<ResultFileFact> files, boolean replayed) {
        DecisionResult withReplay() {
            return new DecisionResult(operationId, tenantId, projectId, projectTaskId, projectTaskVersion,
                    taskId, taskRevisionNo,
                    questionnaireId, templateRevisionId, responseId, resultId, resultVersion, resultFactVersion,
                    collectionKey,
                    sourceOwnerContext, sourceObjectType, sourceObjectId, sourceObjectVersion, score, threshold,
                    passed, ruleVersion, resultStatus, archiveActorUserId, files, true);
        }
    }

    public record ResultFileFact(String role, Integer sequence, FileArtifactVersionFact file) {}
    private record DecisionFile(String role, Integer sequence, FileArtifactVersionFact fact,
                                SatisfactionResultFileDO row) {}
}
