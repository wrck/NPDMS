package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionAccessGrantDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionAccessGrantMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionResponseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SatisfactionResponseFilePolicyProvider implements FileBusinessObjectPolicyProvider {
    static final String OWNER = "ACC";
    static final String TYPE = "SATISFACTION_RESPONSE";
    static final String SIGNATURE = "SATISFACTION_SIGNATURE";
    static final String ATTACHMENT = "SATISFACTION_ATTACHMENT";

    private final SatisfactionAccessGrantMapper grantMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionResponseMapper responseMapper;
    private final SatisfactionResponseReservationService reservationService;
    private final SatisfactionAssistedResponseReservationService assistedReservationService;
    private final ProjectScopeApi projectScopeApi;

    @Override public String ownerContext() { return OWNER; }
    @Override public String objectType() { return TYPE; }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) { return denied(); }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        if (query == null || !(FileActionCodes.ARCHIVE.equals(query.requiredAction())
                || FileActionCodes.REFERENCE.equals(query.requiredAction()))
                || !(SIGNATURE.equals(query.purposeCode()) || ATTACHMENT.equals(query.purposeCode()))) {
            return denied();
        }
        Long responseId = positiveLong(query.objectId());
        var response = responseMapper.selectByIdForUpdate(query.tenantId(), responseId);
        SatisfactionQuestionnaireDO questionnaire = response == null ? null
                : questionnaireMapper.selectByIdForUpdate(query.tenantId(), response.getQuestionnaireId());
        SatisfactionCollectionTaskDO task = questionnaire == null ? null
                : taskMapper.selectByIdForUpdate(query.tenantId(), questionnaire.getCollectionTaskId());
        if (response == null || questionnaire == null || task == null
                || !Objects.equals(task.getAssignedToUserId(), query.actorUserId())) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_ARCHIVE_OWNER_CONFLICT");
        }
        ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                query.tenantId(), query.actorUserId(), task.getProjectId(), ProjectScopeApi.ACTION_EDIT,
                query.expectedScopeVersion()));
        if (scope == null || !Objects.equals(scope.treeVersion(), query.expectedScopeVersion())
                || !scope.fullProjectIds().contains(task.getProjectId())) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_ARCHIVE_SCOPE_CONFLICT");
        }
        return policy(query.purposeCode(), scope.treeVersion());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        FileReferenceSetKey key = query.key();
        if (key == null || !OWNER.equals(key.ownerContext()) || !TYPE.equals(key.objectType())) return denied();
        return lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                query.tenantId(), query.actorUserId(), key.ownerContext(), key.objectType(), key.objectId(),
                key.purposeCode(), "REFERENCE_SET", query.requiredAction(), query.expectedScopeVersion()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BusinessGrantUploadPolicyFact initializeBusinessGrantUploadPolicy(
            BusinessGrantUploadInitializePolicyQuery query) {
        return resolve(query.tenantId(), query.grantId(), query.grantVersion(), query.questionnaireId(),
                query.requestId(), query.responseId(), query.policyKey(), query.fileSlotKey(),
                query.fileSequence(), null, List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BusinessGrantUploadPolicyFact lockAndRevalidateBusinessGrantUpload(
            BusinessGrantUploadCompletePolicyQuery query) {
        return resolve(query.tenantId(), query.grantId(), query.grantVersion(), query.questionnaireId(),
                query.requestId(), query.responseId(), query.policyKey(), query.fileSlotKey(),
                query.fileSequence(), query.expectedScopeVersion(), List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BusinessGrantUploadPolicyFact lockAndRevalidateBusinessGrantFiles(
            BusinessGrantFileRevalidationQuery query) {
        if (query.files().isEmpty()) throw new IllegalArgumentException("SATISFACTION_GRANT_FILES_EMPTY");
        if (query.files().stream().anyMatch(file -> file == null
                || !(SIGNATURE.equals(file.policyKey()) || ATTACHMENT.equals(file.policyKey()))
                || file.fileSlotKey() == null || file.fileSlotKey().isBlank()
                || file.fileSequence() == null || file.fileSequence() <= 0)) {
            throw new IllegalArgumentException("SATISFACTION_GRANT_FILE_IDENTITY_INVALID");
        }
        Long scopeVersion = query.files().getFirst().scopeVersion();
        if (query.files().stream().anyMatch(file -> !Objects.equals(scopeVersion, file.scopeVersion()))) {
            throw new IllegalStateException("SATISFACTION_GRANT_FILE_SCOPE_CONFLICT");
        }
        return resolve(query.tenantId(), query.grantId(), query.grantVersion(), query.questionnaireId(),
                query.requestId(), query.responseId(), null, null, null, scopeVersion, query.files());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuthenticatedAssistedUploadPolicyFact initializeAuthenticatedAssistedUploadPolicy(
            AuthenticatedAssistedUploadInitializePolicyQuery query) {
        return resolveAssisted(query.tenantId(), query.actorUserId(), query.taskId(), query.questionnaireId(),
                query.requestId(), query.responseId(), query.policyKey(), query.fileSlotKey(),
                query.fileSequence(), null, List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuthenticatedAssistedUploadPolicyFact lockAndRevalidateAuthenticatedAssistedUpload(
            AuthenticatedAssistedUploadCompletePolicyQuery query) {
        return resolveAssisted(query.tenantId(), query.actorUserId(), query.taskId(), query.questionnaireId(),
                query.requestId(), query.responseId(), query.policyKey(), query.fileSlotKey(),
                query.fileSequence(), query.expectedScopeVersion(), List.of());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuthenticatedAssistedUploadPolicyFact lockAndRevalidateAuthenticatedAssistedFiles(
            AuthenticatedAssistedFileRevalidationQuery query) {
        if (query.files().isEmpty() || query.files().stream().anyMatch(file -> file == null
                || !(SIGNATURE.equals(file.policyKey()) || ATTACHMENT.equals(file.policyKey()))
                || file.fileSlotKey() == null || file.fileSlotKey().isBlank()
                || file.fileSequence() == null || file.fileSequence() <= 0)) {
            throw new IllegalArgumentException("SATISFACTION_ASSISTED_FILES_INVALID");
        }
        Long scopeVersion = query.files().getFirst().scopeVersion();
        if (query.files().stream().anyMatch(file -> !Objects.equals(scopeVersion, file.scopeVersion()))) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_FILE_SCOPE_CONFLICT");
        }
        return resolveAssisted(query.tenantId(), query.actorUserId(), query.taskId(), query.questionnaireId(),
                query.requestId(), query.responseId(), null, null, null, scopeVersion, query.files());
    }

    private AuthenticatedAssistedUploadPolicyFact resolveAssisted(
            Long tenantId, Long actorUserId, Long taskId, Long questionnaireId,
            String requestId, Long responseId, String policyKey, String fileSlotKey,
            Integer fileSequence, Long expectedScopeVersion, List<AuthenticatedAssistedFileHandle> files) {
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(tenantId, taskId);
        SatisfactionQuestionnaireDO questionnaire = task == null ? null
                : questionnaireMapper.selectByIdForUpdate(tenantId, task.getQuestionnaireId());
        if (task == null || questionnaire == null || !Objects.equals(questionnaireId, questionnaire.getId())
                || !Objects.equals(taskId, questionnaire.getCollectionTaskId())
                || !Objects.equals(actorUserId, task.getAssignedToUserId())
                || !("PENDING_COLLECTION".equals(task.getTaskStatus()) || "ASSIGNED".equals(task.getTaskStatus()))
                || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())
                || responseMapper.selectByIdForUpdate(tenantId, responseId) != null) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_FILE_OWNER_CONFLICT");
        }
        assistedReservationService.requireReserved(tenantId, actorUserId, task, questionnaire,
                requestId, responseId);
        Long expected = expectedScopeVersion == null ? questionnaire.getAccessScopeVersion() : expectedScopeVersion;
        ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, actorUserId, task.getProjectId(), ProjectScopeApi.ACTION_EDIT, expected));
        if (scope == null || !Objects.equals(expected, scope.treeVersion())
                || !scope.fullProjectIds().contains(task.getProjectId())) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_FILE_SCOPE_CONFLICT");
        }
        String effectivePolicy = policyKey == null ? files.getFirst().policyKey() : policyKey;
        return new AuthenticatedAssistedUploadPolicyFact(taskId, questionnaireId, requestId, responseId,
                effectivePolicy, fileSlotKey, fileSequence, actorUserId, scope.treeVersion(),
                policy(effectivePolicy, scope.treeVersion()));
    }

    private BusinessGrantUploadPolicyFact resolve(
            Long tenantId, Long grantId, Integer grantVersion, Long questionnaireId,
            String requestId, Long responseId, String policyKey, String fileSlotKey,
            Integer fileSequence, Long expectedScopeVersion, List<BusinessGrantFileHandle> files) {
        SatisfactionAccessGrantDO grant = grantMapper.selectByIdForUpdate(tenantId, grantId);
        SatisfactionQuestionnaireDO questionnaire = grant == null ? null
                : questionnaireMapper.selectByIdForUpdate(tenantId, grant.getQuestionnaireId());
        SatisfactionCollectionTaskDO task = questionnaire == null ? null
                : taskMapper.selectByIdForUpdate(tenantId, questionnaire.getCollectionTaskId());
        requireIdentity(tenantId, grantId, grantVersion, questionnaireId, grant, questionnaire, task);
        var reservation = reservationService.requireReserved(tenantId, grant, questionnaire, task,
                requestId, responseId);
        Long expected = expectedScopeVersion == null ? questionnaire.getAccessScopeVersion() : expectedScopeVersion;
        ProjectScopeResult scope = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, reservation.grantIssuerUserId(), task.getProjectId(),
                ProjectScopeApi.ACTION_EDIT, expected));
        if (scope == null || !Objects.equals(expected, scope.treeVersion())
                || !scope.fullProjectIds().contains(task.getProjectId())) {
            throw new IllegalStateException("SATISFACTION_GRANT_FILE_SCOPE_CONFLICT");
        }
        String effectivePolicy = policyKey == null ? files.getFirst().policyKey() : policyKey;
        FileBusinessObjectPolicyFact filePolicy = policy(effectivePolicy, scope.treeVersion());
        return new BusinessGrantUploadPolicyFact(grantId, grantVersion, questionnaireId, requestId,
                responseId, effectivePolicy, fileSlotKey, fileSequence,
                reservation.grantIssuerUserId(), scope.treeVersion(), filePolicy);
    }

    private void requireIdentity(Long tenantId, Long grantId, Integer grantVersion, Long questionnaireId,
                                 SatisfactionAccessGrantDO grant, SatisfactionQuestionnaireDO questionnaire,
                                 SatisfactionCollectionTaskDO task) {
        LocalDateTime now = LocalDateTime.now();
        if (grant == null || questionnaire == null || task == null
                || !tenantId.equals(grant.getTenantId()) || !grantId.equals(grant.getId())
                || !grantVersion.equals(grant.getGrantVersion())
                || !questionnaireId.equals(grant.getQuestionnaireId())
                || !questionnaireId.equals(questionnaire.getId())
                || !questionnaire.getCollectionTaskId().equals(task.getId())
                || !"ACTIVE".equals(grant.getGrantStatus()) || now.isBefore(grant.getEffectiveFrom())
                || !now.isBefore(grant.getExpiresAt())
                || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())
                || !("PENDING_COLLECTION".equals(task.getTaskStatus())
                || "ASSIGNED".equals(task.getTaskStatus()))
                || SatisfactionResponseReservationService.positiveLong(grant.getCreator()) == null) {
            throw new IllegalStateException("SATISFACTION_GRANT_FILE_OWNER_CONFLICT");
        }
    }

    private FileBusinessObjectPolicyFact policy(String policyKey, Long scopeVersion) {
        if (SIGNATURE.equals(policyKey)) {
            return new FileBusinessObjectPolicyFact(true, scopeVersion, "IMMUTABLE", "SINGLE",
                    Set.of(SIGNATURE), Set.of("image/png", "image/jpeg", "application/pdf"),
                    10_485_760L, "CONFIDENTIAL");
        }
        if (ATTACHMENT.equals(policyKey)) {
            return new FileBusinessObjectPolicyFact(true, scopeVersion, "IMMUTABLE", "MULTIPLE",
                    Set.of(ATTACHMENT), Set.of("image/png", "image/jpeg", "application/pdf"),
                    52_428_800L, "CONFIDENTIAL");
        }
        throw new IllegalArgumentException("SATISFACTION_GRANT_FILE_POLICY_INVALID");
    }

    private FileBusinessObjectPolicyFact denied() {
        return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null);
    }

    private Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("SATISFACTION_RESPONSE_FILE_OBJECT_INVALID");
    }
}
