package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
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
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SatisfactionResponseSubmissionService {
    private final SatisfactionAccessGrantMapper grantMapper;
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionResponseMapper responseMapper;
    private final SatisfactionResponseFileMapper responseFileMapper;

    @Transactional(rollbackFor = Exception.class)
    public SubmissionResult submit(Command command) {
        requireCommand(command);
        SatisfactionAccessGrantDO grant = grantMapper.selectByDigestForUpdate(
                new SatisfactionGrantDigestQuery(command.tenantId(), digest(command.token())));
        if (grant == null) throw new IllegalStateException("SATISFACTION_GRANT_UNAVAILABLE");

        SatisfactionQuestionnaireDO initial = questionnaireMapper.selectById(grant.getQuestionnaireId());
        if (initial == null || !command.tenantId().equals(initial.getTenantId())) {
            throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_UNAVAILABLE");
        }
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(
                command.tenantId(), initial.getCollectionTaskId());
        SatisfactionQuestionnaireDO questionnaire = questionnaireMapper.selectByIdForUpdate(
                command.tenantId(), initial.getId());
        if (task == null || questionnaire == null || !task.getId().equals(questionnaire.getCollectionTaskId())) {
            throw new IllegalStateException("SATISFACTION_TASK_IDENTITY_CONFLICT");
        }

        SatisfactionResponseDO existing = responseMapper.selectByIdentityForUpdate(
                new SatisfactionResponseIdentityQuery(command.tenantId(), questionnaire.getId(), command.requestId()));
        if (existing != null) return replay(existing, command, task.getId());

        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equals(grant.getGrantStatus()) || now.isBefore(grant.getEffectiveFrom())
                || !now.isBefore(grant.getExpiresAt()) || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())
                || !("PENDING_COLLECTION".equals(task.getTaskStatus()) || "ASSIGNED".equals(task.getTaskStatus()))) {
            throw new IllegalStateException("SATISFACTION_SUBMISSION_NOT_ALLOWED");
        }

        long responseId = IdWorker.getId();
        SatisfactionResponseDO response = new SatisfactionResponseDO();
        response.setId(responseId);
        response.setTenantId(command.tenantId());
        response.setQuestionnaireId(questionnaire.getId());
        response.setResponseNo(responseMapper.selectNextResponseNo(command.tenantId(), questionnaire.getId()));
        response.setRequestId(command.requestId());
        response.setSubmitChannel(command.submitChannel());
        response.setCustomerContactRef(command.customerContactRef());
        response.setAssistedByUserId(command.assistedByUserId());
        response.setAnswerSnapshot(command.answerSnapshot());
        response.setSubmittedAt(now);
        response.setCreator(command.actorRef());
        if (responseMapper.insert(response) != 1) throw new IllegalStateException("SATISFACTION_RESPONSE_CREATE_FAILED");
        for (FileFact fact : command.files()) insertFile(command, responseId, fact);
        if (grantMapper.consumeIfActive(new SatisfactionGrantConsumeUpdate(command.tenantId(), grant.getId(),
                grant.getVersion(), now, command.actorRef())) != 1
                || taskMapper.moveToPendingDecision(new SatisfactionTaskDecisionUpdate(command.tenantId(),
                task.getId(), task.getVersion(), command.actorRef())) != 1) {
            throw new IllegalStateException("SATISFACTION_SUBMISSION_STATE_CONFLICT");
        }
        return new SubmissionResult(responseId, questionnaire.getId(), task.getId(), false);
    }

    private void insertFile(Command command, long responseId, FileFact fact) {
        SatisfactionResponseFileDO row = new SatisfactionResponseFileDO();
        row.setId(IdWorker.getId()); row.setTenantId(command.tenantId()); row.setResponseId(responseId);
        row.setFileRole(fact.role()); row.setFileSequence(fact.sequence()); row.setArtifactId(fact.artifactId());
        row.setVersionNo(fact.versionNo()); row.setReferenceKey(fact.referenceKey());
        row.setArtifactVersion(fact.artifactVersion()); row.setReferenceVersion(fact.referenceVersion());
        row.setAvailabilityVersion(fact.availabilityVersion()); row.setScopeVersion(fact.scopeVersion());
        row.setFileHash(fact.sha256()); row.setCreator(command.actorRef());
        if (responseFileMapper.insert(row) != 1) throw new IllegalStateException("SATISFACTION_RESPONSE_FILE_CREATE_FAILED");
    }

    private SubmissionResult replay(SatisfactionResponseDO existing, Command command, Long taskId) {
        if (!existing.getAnswerSnapshot().equals(command.answerSnapshot())
                || !existing.getSubmitChannel().equals(command.submitChannel())
                || !existing.getCustomerContactRef().equals(command.customerContactRef())) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_IDEMPOTENCY_CONFLICT");
        }
        return new SubmissionResult(existing.getId(), existing.getQuestionnaireId(), taskId, true);
    }

    private void requireCommand(Command command) {
        if (command == null || command.tenantId() == null || command.token() == null || command.token().isBlank()
                || command.requestId() == null || command.requestId().isBlank()
                || command.answerSnapshot() == null || command.answerSnapshot().isBlank()
                || command.customerContactRef() == null || command.customerContactRef().isBlank()
                || command.actorRef() == null || command.actorRef().isBlank()
                || command.files() == null || command.files().stream().filter(f -> "SIGNATURE".equals(f.role())).count() != 1) {
            throw new IllegalArgumentException("SATISFACTION_RESPONSE_INVALID");
        }
        if (!("PUBLIC_LINK".equals(command.submitChannel()) || "ASSISTED".equals(command.submitChannel()))
                || ("ASSISTED".equals(command.submitChannel()) && command.assistedByUserId() == null)
                || ("PUBLIC_LINK".equals(command.submitChannel()) && command.assistedByUserId() != null)) {
            throw new IllegalArgumentException("SATISFACTION_RESPONSE_CHANNEL_INVALID");
        }
        Set<String> fileKeys = new HashSet<>();
        for (FileFact fact : command.files()) {
            if (fact == null || !Set.of("SIGNATURE", "ATTACHMENT").contains(fact.role())
                    || fact.sequence() == null || fact.sequence() <= 0 || fact.artifactId() == null
                    || fact.versionNo() == null || fact.referenceKey() == null || fact.referenceKey().isBlank()
                    || fact.artifactVersion() == null || fact.referenceVersion() == null
                    || fact.availabilityVersion() == null || fact.scopeVersion() == null
                    || fact.sha256() == null || !fact.sha256().matches("[0-9a-f]{64}")
                    || !fileKeys.add(fact.role() + ":" + fact.sequence())) {
                throw new IllegalArgumentException("SATISFACTION_RESPONSE_FILE_INVALID");
            }
        }
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Command(Long tenantId, String token, String requestId, String submitChannel,
                          String customerContactRef, Long assistedByUserId, String answerSnapshot,
                          List<FileFact> files, String actorRef) {}
    public record FileFact(String role, Integer sequence, Long artifactId, Integer versionNo,
                           String referenceKey, Integer artifactVersion, Integer referenceVersion,
                           Integer availabilityVersion, Long scopeVersion, String sha256) {}
    public record SubmissionResult(Long responseId, Long questionnaireId, Long taskId, boolean replayed) {}
}
