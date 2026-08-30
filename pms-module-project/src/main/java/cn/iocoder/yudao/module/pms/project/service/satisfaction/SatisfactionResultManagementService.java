package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultFileDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.*;
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
public class SatisfactionResultManagementService {
    private static final String TASK_CODE = "T-SAT-SURVEY";
    private final SatisfactionResultMapper resultMapper;
    private final SatisfactionResultFileMapper fileMapper;
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final FileArtifactApi fileArtifactApi;
    private final PlatformCommandExecutionApi commandExecutionApi;

    public List<ResultView> list(Long tenantId, Long actorUserId, Long projectId) {
        Set<Long> scope = projectId == null
                ? projectScopeApi.resolveAllCurrent(new ProjectAllScopeQuery(tenantId, actorUserId,
                ProjectScopeApi.ACTION_VIEW))
                : requireScope(tenantId, actorUserId, projectId, ProjectScopeApi.ACTION_VIEW);
        if (scope == null || scope.isEmpty()) return List.of();
        return resultMapper.selectByScope(new SatisfactionResultScopeQuery(tenantId, scope, null)).stream()
                .map(this::view).toList();
    }

    public ResultView get(Long tenantId, Long actorUserId, Long resultId) {
        SatisfactionResultDO result = resultMapper.selectById(resultId);
        if (result == null || !tenantId.equals(result.getTenantId())) throw unavailable();
        SatisfactionCollectionTaskDO task = taskMapper.selectById(result.getCollectionTaskId());
        if (task == null || !tenantId.equals(task.getTenantId())) throw unavailable();
        Set<Long> scope = requireScope(tenantId, actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_VIEW);
        return resultMapper.selectByScope(new SatisfactionResultScopeQuery(tenantId, scope, resultId)).stream()
                .findFirst().map(this::view).orElseThrow(this::unavailable);
    }

    public DownloadFact download(Long tenantId, Long actorUserId, Long resultId, Integer sequence) {
        ResultView result = get(tenantId, actorUserId, resultId);
        SatisfactionResultFileDO row = fileMapper.selectListByResult(
                        new SatisfactionResultFilesQuery(tenantId, resultId)).stream()
                .sorted(fileOrder()).skip(sequence - 1L).findFirst().orElseThrow(this::unavailable);
        String objectType = "RESULT_DOCUMENT".equals(row.getFileRole())
                ? "SATISFACTION_RESULT" : "SATISFACTION_RESPONSE";
        Long objectId = "SATISFACTION_RESULT".equals(objectType) ? resultId : result.responseId();
        String purpose = switch (row.getFileRole()) {
            case "RESULT_DOCUMENT" -> "SATISFACTION_RESULT_DOCUMENT";
            case "SIGNATURE" -> "SATISFACTION_SIGNATURE";
            case "ATTACHMENT" -> "SATISFACTION_ATTACHMENT";
            default -> throw unavailable();
        };
        FileArtifactVersionFact fact = fileArtifactApi.inspect(new FileArtifactVersionQuery(
                row.getArtifactId(), row.getVersionNo(), "ACC", objectType, String.valueOf(objectId),
                purpose, row.getReferenceKey(), FileActionCodes.DOWNLOAD));
        if (!matches(row, fact)) throw unavailable();
        return new DownloadFact(sequence, row.getFileRole(), row.getFileSequence(), fact);
    }

    public InvalidationResult invalidate(Long tenantId, Long actorUserId, Long resultId,
                                         Integer expectedFactVersion, String reasonCode,
                                         String reasonSummary, String operationId) {
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        tenantId, "ACC_SATISFACTION_RESULT_INVALIDATE", actorUserId, operationId),
                digest(List.of(resultId, expectedFactVersion, reasonCode, Objects.toString(reasonSummary, ""))),
                InvalidationResult.class,
                () -> invalidateOnce(tenantId, actorUserId, resultId, expectedFactVersion,
                        reasonCode, reasonSummary, operationId), this::invalidationFacts);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IllegalStateException("SATISFACTION_RESULT_INVALIDATION_IDEMPOTENCY_CONFLICT");
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) {
            throw new IllegalStateException("SATISFACTION_RESULT_INVALIDATION_IN_PROGRESS");
        }
        return execution.response();
    }

    @Transactional(rollbackFor = Exception.class)
    protected InvalidationResult invalidateOnce(Long tenantId, Long actorUserId, Long resultId,
                                                  Integer expectedFactVersion, String reasonCode,
                                                  String reasonSummary, String operationId) {
        SatisfactionResultDO initial = resultMapper.selectById(resultId);
        if (initial == null || !tenantId.equals(initial.getTenantId())) throw unavailable();
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(tenantId, initial.getCollectionTaskId());
        SatisfactionResultDO result = resultMapper.selectByIdForUpdate(tenantId, resultId);
        if (task == null || result == null || !Objects.equals(task.getResultId(), resultId)
                || !Objects.equals(result.getVersion(), expectedFactVersion)
                || !"EFFECTIVE".equals(result.getResultStatus()) || !Boolean.TRUE.equals(result.getPassed())
                || result.getEffectiveTo() != null) {
            throw new IllegalStateException("SATISFACTION_RESULT_INVALIDATION_STATE_CONFLICT");
        }
        requireScope(tenantId, actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_EDIT);
        ProjectSatisfactionTaskFact projectTask = workBindingFactApi.lockCurrentSatisfactionTask(
                new ProjectSatisfactionTaskIdentityQuery(task.getProjectId(), task.getProjectTaskId()));
        if (projectTask == null || !TASK_CODE.equals(projectTask.taskCode())
                || !Objects.equals(projectTask.projectId(), task.getProjectId())
                || !Objects.equals(projectTask.projectTaskId(), task.getProjectTaskId())) {
            throw new IllegalStateException("SATISFACTION_RESULT_PROJECT_TASK_CONFLICT");
        }
        LocalDateTime now = LocalDateTime.now();
        if (resultMapper.invalidateCurrent(new SatisfactionResultInvalidationUpdate(tenantId, resultId,
                expectedFactVersion, reasonCode, reasonSummary, actorUserId, now,
                String.valueOf(actorUserId))) != 1) {
            throw new IllegalStateException("SATISFACTION_RESULT_INVALIDATION_VERSION_CONFLICT");
        }
        List<EventFile> files = eventFiles(tenantId, resultId);
        return new InvalidationResult(operationId, tenantId, task.getProjectId(), task.getProjectTaskId(),
                projectTask.projectTaskVersion(), task.getCollectionKey(), task.getTaskRevisionNo(), task.getId(),
                result.getQuestionnaireId(), result.getResponseId(), resultId, result.getResultVersion(),
                expectedFactVersion + 1, task.getSourceOwnerContext(), task.getSourceObjectType(),
                task.getSourceObjectId(), task.getSourceObjectVersion(), result.getThreshold(), result.getRuleVersion(),
                result.getArchiveActorUserId(), reasonCode, actorUserId, now, files);
    }

    private PlatformCommandExecutionApi.SuccessFacts invalidationFacts(InvalidationResult result) {
        String eventId = result.operationId() + ":result-invalidated";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId); payload.put("changeType", "INVALIDATED");
        payload.put("tenantId", result.tenantId()); payload.put("projectId", result.projectId());
        payload.put("projectTaskId", result.projectTaskId()); payload.put("projectTaskVersion", result.projectTaskVersion());
        payload.put("taskCode", TASK_CODE); payload.put("collectionKey", result.collectionKey());
        payload.put("taskRevisionNo", result.taskRevisionNo()); payload.put("taskId", result.taskId());
        payload.put("questionnaireId", result.questionnaireId()); payload.put("responseId", result.responseId());
        payload.put("resultId", result.resultId()); payload.put("resultVersion", result.resultVersion());
        payload.put("resultFactVersion", result.resultFactVersion()); payload.put("ruleVersion", result.ruleVersion());
        payload.put("threshold", result.threshold()); payload.put("sourceOwnerContext", result.sourceOwnerContext());
        payload.put("sourceObjectType", result.sourceObjectType()); payload.put("sourceObjectId", result.sourceObjectId());
        payload.put("sourceObjectVersion", result.sourceObjectVersion()); payload.put("passed", true);
        payload.put("resultStatus", "INVALIDATED"); payload.put("archiveActorUserId", result.archiveActorUserId());
        payload.put("invalidationReasonCode", result.reasonCode());
        payload.put("invalidatedByUserId", result.invalidatedByUserId()); payload.put("invalidatedAt", result.invalidatedAt());
        payload.put("files", result.files());
        return new PlatformCommandExecutionApi.SuccessFacts("SATISFACTION_RESULT_INVALIDATED", "SatisfactionResult",
                String.valueOf(result.resultId()), result.operationId(), JsonUtils.toJsonString(result),
                List.of(new PlatformCommandExecutionApi.BusinessEvent(eventId,
                        "SatisfactionResultVersionChanged", JsonUtils.toJsonString(payload))));
    }

    private List<EventFile> eventFiles(Long tenantId, Long resultId) {
        List<SatisfactionResultFileDO> rows = fileMapper.selectListByResult(
                new SatisfactionResultFilesQuery(tenantId, resultId)).stream().sorted(fileOrder()).toList();
        List<EventFile> files = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            SatisfactionResultFileDO row = rows.get(i);
            files.add(new EventFile(row.getFileRole(), row.getFileSequence(), i + 1, row.getArtifactId(),
                    row.getVersionNo(), row.getReferenceKey(), row.getArtifactVersion(), row.getReferenceVersion(),
                    row.getAvailabilityVersion(), row.getScopeVersion(), row.getFileHash()));
        }
        return List.copyOf(files);
    }

    private Comparator<SatisfactionResultFileDO> fileOrder() {
        Map<String, Integer> role = Map.of("RESULT_DOCUMENT", 0, "SIGNATURE", 1, "ATTACHMENT", 2);
        return Comparator.comparing((SatisfactionResultFileDO row) -> role.getOrDefault(row.getFileRole(), 99))
                .thenComparing(SatisfactionResultFileDO::getFileSequence);
    }

    private ResultView view(SatisfactionResultViewRecord row) {
        return new ResultView(row.resultId(), row.projectId(), row.projectTaskId(), row.collectionTaskId(),
                row.taskRevisionNo(), row.questionnaireId(), row.responseId(), row.resultVersion(), row.factVersion(),
                row.score(), row.threshold(), Boolean.TRUE.equals(row.passed()), row.ruleVersion(), row.resultStatus(),
                row.archiveStatus(), row.effectiveFrom(), row.effectiveTo());
    }

    private Set<Long> requireScope(Long tenantId, Long actorUserId, Long projectId, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorUserId, projectId, action));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) throw unavailable();
        return scope.fullProjectIds();
    }

    private boolean matches(SatisfactionResultFileDO row, FileArtifactVersionFact fact) {
        return fact != null && fact.fileFactVersion() != null
                && Objects.equals(row.getArtifactId(), fact.artifactId())
                && Objects.equals(row.getVersionNo(), fact.versionNo())
                && Objects.equals(row.getReferenceKey(), fact.referenceKey())
                && Objects.equals(row.getArtifactVersion(), fact.fileFactVersion().artifactVersion())
                && Objects.equals(row.getReferenceVersion(), fact.fileFactVersion().referenceVersion())
                && Objects.equals(row.getAvailabilityVersion(), fact.fileFactVersion().availabilityVersion())
                && Objects.equals(row.getScopeVersion(), fact.scopeVersion()) && Objects.equals(row.getFileHash(), fact.sha256())
                && "AVAILABLE".equals(fact.availabilityStatus()) && "ACTIVE".equals(fact.referenceStatus());
    }

    private IllegalStateException unavailable() { return new IllegalStateException("SATISFACTION_RESULT_NOT_FOUND"); }

    private String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record ResultView(Long resultId, Long projectId, Long projectTaskId, Long taskId, Integer taskRevisionNo,
                             Long questionnaireId, Long responseId, Integer resultVersion, Integer factVersion,
                             java.math.BigDecimal score, java.math.BigDecimal threshold, boolean passed,
                             String ruleVersion, String resultStatus, String archiveStatus,
                             LocalDateTime effectiveFrom, LocalDateTime effectiveTo) {}
    public record DownloadFact(Integer sourceSequence, String role, Integer roleSequence,
                               FileArtifactVersionFact file) {}
    public record EventFile(String role, Integer sequence, Integer sourceSequence, Long artifactId, Integer versionNo,
                            String referenceKey, Integer artifactVersion, Integer referenceVersion,
                            Integer availabilityVersion, Long scopeVersion, String sha256) {}
    public record InvalidationResult(String operationId, Long tenantId, Long projectId, Long projectTaskId,
                                     Integer projectTaskVersion, String collectionKey, Integer taskRevisionNo,
                                     Long taskId, Long questionnaireId, Long responseId, Long resultId,
                                     Integer resultVersion, Integer resultFactVersion, String sourceOwnerContext,
                                     String sourceObjectType, String sourceObjectId, Long sourceObjectVersion,
                                     java.math.BigDecimal threshold, String ruleVersion, Long archiveActorUserId,
                                     String reasonCode, Long invalidatedByUserId, LocalDateTime invalidatedAt,
                                     List<EventFile> files) {}
}
