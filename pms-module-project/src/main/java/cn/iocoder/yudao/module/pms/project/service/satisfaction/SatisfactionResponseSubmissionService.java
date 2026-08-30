package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.*;
import cn.iocoder.yudao.module.pms.project.domain.satisfaction.SatisfactionQuestionnaireDefinition;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileHandle;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFilesRevalidationCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionItem;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ExistingFileReferenceTarget;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SatisfactionResponseSubmissionService {
    private final SatisfactionAccessGrantMapper grantMapper;
    private final SatisfactionCollectionTaskMapper taskMapper;
    private final SatisfactionQuestionnaireMapper questionnaireMapper;
    private final SatisfactionResponseMapper responseMapper;
    private final SatisfactionResponseFileMapper responseFileMapper;
    private final SatisfactionResponseReservationService reservationService;
    private final FileArtifactApi fileArtifactApi;

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
        if (existing != null) return replay(existing, command, grant, task.getId(), questionnaire);

        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equals(grant.getGrantStatus()) || now.isBefore(grant.getEffectiveFrom())
                || !now.isBefore(grant.getExpiresAt()) || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())
                || !("PENDING_COLLECTION".equals(task.getTaskStatus()) || "ASSIGNED".equals(task.getTaskStatus()))) {
            throw new IllegalStateException("SATISFACTION_SUBMISSION_NOT_ALLOWED");
        }

        SatisfactionQuestionnaireDefinition.Evaluation evaluation = evaluate(questionnaire, command.answerSnapshot());

        List<FileFact> canonicalFiles = command.files();
        long responseId;
        if ("PUBLIC_LINK".equals(command.submitChannel())) {
            var reservation = reservationService.requireReserved(command.tenantId(), grant, questionnaire, task,
                    command.requestId(), command.reservedResponseId());
            responseId = reservation.responseId();
            canonicalFiles = canonicalFiles(fileArtifactApi.lockAndRevalidateBusinessGrantFiles(
                    new BusinessGrantFilesRevalidationCommand(command.tenantId(), grant.getId(),
                            grant.getGrantVersion(), questionnaire.getId(), command.requestId(), responseId,
                            command.files().stream().map(this::toHandle).toList())));
        } else {
            responseId = IdWorker.getId();
        }
        String actorRef = "PUBLIC_LINK".equals(command.submitChannel())
                ? "BUSINESS_GRANT:" + grant.getId() : command.actorRef();
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
        response.setCreator(actorRef);
        if (responseMapper.insert(response) != 1) throw new IllegalStateException("SATISFACTION_RESPONSE_CREATE_FAILED");
        for (FileFact fact : canonicalFiles) insertFile(command, responseId, fact, actorRef);
        if (grantMapper.consumeIfActive(new SatisfactionGrantConsumeUpdate(command.tenantId(), grant.getId(),
                grant.getVersion(), now, actorRef)) != 1
                || taskMapper.moveToPendingDecision(new SatisfactionTaskDecisionUpdate(command.tenantId(),
                task.getId(), task.getVersion(), actorRef)) != 1) {
            throw new IllegalStateException("SATISFACTION_SUBMISSION_STATE_CONFLICT");
        }
        return new SubmissionResult(responseId, questionnaire.getId(), task.getId(), false,
                evaluation.score(), evaluation.threshold(), evaluation.passed(), evaluation.ruleVersion());
    }

    @Transactional(rollbackFor = Exception.class)
    public SubmissionResult submitAssisted(AssistedCommand command) {
        if (command == null || command.tenantId() == null || command.actorUserId() == null
                || command.actorUserId() <= 0 || command.taskId() == null || command.requestId() == null
                || command.requestId().isBlank() || command.customerContactRef() == null
                || command.customerContactRef().isBlank() || command.answerSnapshot() == null
                || command.answerSnapshot().isBlank() || command.files() == null
                || command.files().stream().filter(file -> "SIGNATURE".equals(file.role())).count() != 1) {
            throw new IllegalArgumentException("SATISFACTION_ASSISTED_RESPONSE_INVALID");
        }
        SatisfactionCollectionTaskDO task = taskMapper.selectByIdForUpdate(command.tenantId(), command.taskId());
        SatisfactionQuestionnaireDO questionnaire = task == null ? null : questionnaireMapper.selectByIdForUpdate(
                command.tenantId(), task.getQuestionnaireId());
        if (task == null || questionnaire == null || !command.actorUserId().equals(task.getAssignedToUserId())
                || !("PENDING_COLLECTION".equals(task.getTaskStatus()) || "ASSIGNED".equals(task.getTaskStatus())
                || "PENDING_DECISION".equals(task.getTaskStatus()))
                || !"ACTIVE".equals(questionnaire.getQuestionnaireStatus())) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_RESPONSE_NOT_ALLOWED");
        }
        SatisfactionResponseDO existing = responseMapper.selectByIdentityForUpdate(new SatisfactionResponseIdentityQuery(
                command.tenantId(), questionnaire.getId(), command.requestId()));
        if (existing != null) {
            if (!Objects.equals(existing.getAnswerSnapshot(), command.answerSnapshot())
                    || !Objects.equals(existing.getCustomerContactRef(), command.customerContactRef())
                    || !Objects.equals(existing.getAssistedByUserId(), command.actorUserId())
                    || !sameAssistedFiles(command.tenantId(), existing.getId(),
                    attachAssistedFiles(command, existing.getId(), questionnaire))) {
                throw new IllegalStateException("SATISFACTION_RESPONSE_IDEMPOTENCY_CONFLICT");
            }
            SatisfactionQuestionnaireDefinition.Evaluation evaluation = evaluate(questionnaire, existing.getAnswerSnapshot());
            return new SubmissionResult(existing.getId(), questionnaire.getId(), task.getId(), true,
                    evaluation.score(), evaluation.threshold(), evaluation.passed(), evaluation.ruleVersion());
        }
        if (!("PENDING_COLLECTION".equals(task.getTaskStatus()) || "ASSIGNED".equals(task.getTaskStatus()))) {
            throw new IllegalStateException("SATISFACTION_ASSISTED_RESPONSE_NOT_ALLOWED");
        }
        SatisfactionQuestionnaireDefinition.Evaluation evaluation = evaluate(questionnaire, command.answerSnapshot());
        long responseId = IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();
        SatisfactionResponseDO response = new SatisfactionResponseDO();
        response.setId(responseId); response.setTenantId(command.tenantId());
        response.setQuestionnaireId(questionnaire.getId());
        response.setResponseNo(responseMapper.selectNextResponseNo(command.tenantId(), questionnaire.getId()));
        response.setRequestId(command.requestId()); response.setSubmitChannel("ASSISTED");
        response.setCustomerContactRef(command.customerContactRef());
        response.setAssistedByUserId(command.actorUserId()); response.setAnswerSnapshot(command.answerSnapshot());
        response.setSubmittedAt(now); response.setCreator(String.valueOf(command.actorUserId()));
        if (responseMapper.insert(response) != 1) throw new IllegalStateException("SATISFACTION_RESPONSE_CREATE_FAILED");

        List<AttachedAssistedFile> attached = attachAssistedFiles(command, responseId, questionnaire);
        for (AttachedAssistedFile file : attached) {
            FileArtifactVersionFact fact = file.fact();
            insertFile(new Command(command.tenantId(), "assisted", command.requestId(), "ASSISTED",
                    command.customerContactRef(), command.actorUserId(), command.answerSnapshot(), List.of(),
                    String.valueOf(command.actorUserId())), responseId,
                    new FileFact(file.role(), fact.referenceKey(), file.sequence(), fact.artifactId(),
                            fact.versionNo(), fact.referenceKey(), fact.fileFactVersion().artifactVersion(),
                            fact.fileFactVersion().referenceVersion(), fact.fileFactVersion().availabilityVersion(),
                            fact.scopeVersion(), fact.sha256()), String.valueOf(command.actorUserId()));
        }
        if (taskMapper.moveToPendingDecision(new SatisfactionTaskDecisionUpdate(command.tenantId(), task.getId(),
                task.getVersion(), String.valueOf(command.actorUserId()))) != 1) {
            throw new IllegalStateException("SATISFACTION_SUBMISSION_STATE_CONFLICT");
        }
        return new SubmissionResult(responseId, questionnaire.getId(), task.getId(), false,
                evaluation.score(), evaluation.threshold(), evaluation.passed(), evaluation.ruleVersion());
    }

    private List<AttachedAssistedFile> attachAssistedFiles(AssistedCommand command, Long responseId,
                                                            SatisfactionQuestionnaireDO questionnaire) {
        List<AssistedFile> ordered = command.files().stream()
                .sorted(Comparator.comparing(AssistedFile::role).thenComparing(AssistedFile::sequence)).toList();
        List<AttachExistingFileVersionItem> items = new java.util.ArrayList<>(ordered.size());
        for (AssistedFile file : ordered) {
            if (!Set.of("SIGNATURE", "ATTACHMENT").contains(file.role()) || file.sequence() == null
                    || file.sequence() <= 0 || file.source() == null) {
                throw new IllegalArgumentException("SATISFACTION_ASSISTED_FILE_INVALID");
            }
            String referenceKey = java.util.UUID.nameUUIDFromBytes(
                    (responseId + ":" + file.role() + ":" + file.sequence()).getBytes(StandardCharsets.UTF_8))
                    .toString();
            items.add(new AttachExistingFileVersionItem(file.source(), new ExistingFileReferenceTarget(
                    "ACC", "SATISFACTION_RESPONSE", String.valueOf(responseId), policyKey(file.role()),
                    referenceKey, questionnaire.getAccessScopeVersion())));
        }
        List<FileArtifactVersionFact> facts = fileArtifactApi.attachExistingVersions(
                new AttachExistingFileVersionsCommand(command.requestId() + ":assisted-files", items));
        if (facts.size() != ordered.size()) throw new IllegalStateException("SATISFACTION_ASSISTED_FILE_CONFLICT");
        List<AttachedAssistedFile> attached = new java.util.ArrayList<>(facts.size());
        for (int i = 0; i < facts.size(); i++) {
            attached.add(new AttachedAssistedFile(ordered.get(i).role(), ordered.get(i).sequence(), facts.get(i)));
        }
        return List.copyOf(attached);
    }

    private boolean sameAssistedFiles(Long tenantId, Long responseId, List<AttachedAssistedFile> requested) {
        List<String> actual = responseFileMapper.selectListByResponse(
                        new SatisfactionResponseFilesQuery(tenantId, responseId)).stream()
                .map(row -> row.getFileRole() + "|" + row.getFileSequence() + "|" + row.getArtifactId() + "|"
                        + row.getVersionNo() + "|" + row.getArtifactVersion() + "|" + row.getReferenceVersion()
                        + "|" + row.getAvailabilityVersion() + "|" + row.getScopeVersion())
                .sorted().toList();
        List<String> expected = requested.stream().map(file -> {
            FileArtifactVersionFact fact = file.fact();
            return file.role() + "|" + file.sequence() + "|" + fact.artifactId() + "|" + fact.versionNo()
                    + "|" + fact.fileFactVersion().artifactVersion() + "|"
                    + fact.fileFactVersion().referenceVersion() + "|"
                    + fact.fileFactVersion().availabilityVersion() + "|" + fact.scopeVersion();
        }).sorted().toList();
        return actual.equals(expected);
    }

    private void insertFile(Command command, long responseId, FileFact fact, String actorRef) {
        SatisfactionResponseFileDO row = new SatisfactionResponseFileDO();
        row.setId(IdWorker.getId()); row.setTenantId(command.tenantId()); row.setResponseId(responseId);
        row.setFileRole(fact.role()); row.setFileSequence(fact.sequence()); row.setArtifactId(fact.artifactId());
        row.setVersionNo(fact.versionNo()); row.setReferenceKey(fact.referenceKey());
        row.setArtifactVersion(fact.artifactVersion()); row.setReferenceVersion(fact.referenceVersion());
        row.setAvailabilityVersion(fact.availabilityVersion()); row.setScopeVersion(fact.scopeVersion());
        row.setFileHash(fact.sha256()); row.setCreator(actorRef);
        if (responseFileMapper.insert(row) != 1) throw new IllegalStateException("SATISFACTION_RESPONSE_FILE_CREATE_FAILED");
    }

    private SubmissionResult replay(SatisfactionResponseDO existing, Command command, SatisfactionAccessGrantDO grant,
                                    Long taskId, SatisfactionQuestionnaireDO questionnaire) {
        if (command.reservedResponseId() != null && !command.reservedResponseId().equals(existing.getId())) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_RESERVATION_CONFLICT");
        }
        if ("PUBLIC_LINK".equals(command.submitChannel())
                && !("BUSINESS_GRANT:" + grant.getId()).equals(existing.getCreator())) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_IDEMPOTENCY_CONFLICT");
        }
        if (!existing.getAnswerSnapshot().equals(command.answerSnapshot())
                || !existing.getSubmitChannel().equals(command.submitChannel())
                || !existing.getCustomerContactRef().equals(command.customerContactRef())) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_IDEMPOTENCY_CONFLICT");
        }
        List<FileFact> persistedFiles = responseFileMapper.selectListByResponse(
                        new SatisfactionResponseFilesQuery(command.tenantId(), existing.getId())).stream()
                .map(this::persistedFileFact).toList();
        boolean publicLink = "PUBLIC_LINK".equals(command.submitChannel());
        if (!stableFiles(command.files(), publicLink).equals(stableFiles(persistedFiles, publicLink))) {
            throw new IllegalStateException("SATISFACTION_RESPONSE_IDEMPOTENCY_CONFLICT");
        }
        SatisfactionQuestionnaireDefinition.Evaluation evaluation = evaluate(questionnaire, existing.getAnswerSnapshot());
        return new SubmissionResult(existing.getId(), existing.getQuestionnaireId(), taskId, true,
                evaluation.score(), evaluation.threshold(), evaluation.passed(), evaluation.ruleVersion());
    }

    private SatisfactionQuestionnaireDefinition.Evaluation evaluate(SatisfactionQuestionnaireDO questionnaire,
                                                                     String answerSnapshot) {
        SatisfactionQuestionnaireDefinition.Evaluation evaluation = SatisfactionQuestionnaireDefinition
                .parse(questionnaire.getFrozenQuestionJson()).evaluate(answerSnapshot, true);
        if (questionnaire.getFrozenThreshold() == null || questionnaire.getRuleVersion() == null
                || questionnaire.getFrozenThreshold().compareTo(evaluation.threshold()) != 0
                || !questionnaire.getRuleVersion().equals(evaluation.ruleVersion())) {
            throw new IllegalStateException("SATISFACTION_QUESTIONNAIRE_PROJECTION_CONFLICT");
        }
        return evaluation;
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
                    || ("PUBLIC_LINK".equals(command.submitChannel())
                    && (fact.fileSlotKey() == null || fact.fileSlotKey().isBlank()))
                    || fact.versionNo() == null || fact.referenceKey() == null || fact.referenceKey().isBlank()
                    || fact.artifactVersion() == null || fact.referenceVersion() == null
                    || fact.availabilityVersion() == null || fact.scopeVersion() == null
                    || fact.sha256() == null || !fact.sha256().matches("[0-9a-f]{64}")
                    || !fileKeys.add(fact.role() + ":" + fact.sequence())) {
                throw new IllegalArgumentException("SATISFACTION_RESPONSE_FILE_INVALID");
            }
        }
    }

    private BusinessGrantFileHandle toHandle(FileFact fact) {
        return new BusinessGrantFileHandle(policyKey(fact.role()), fact.fileSlotKey(), fact.sequence(),
                fact.artifactId(), fact.versionNo(), fact.referenceKey(), fact.artifactVersion(),
                fact.referenceVersion(), fact.availabilityVersion(), fact.scopeVersion(), fact.sha256());
    }

    private List<FileFact> canonicalFiles(List<BusinessGrantFileFact> facts) {
        return facts.stream().map(fact -> new FileFact(role(fact.policyKey()), fact.fileSlotKey(),
                fact.fileSequence(), fact.fileFact().artifactId(), fact.fileFact().versionNo(),
                fact.fileFact().referenceKey(), fact.fileFact().fileFactVersion().artifactVersion(),
                fact.fileFact().fileFactVersion().referenceVersion(),
                fact.fileFact().fileFactVersion().availabilityVersion(), fact.fileFact().scopeVersion(),
                fact.fileFact().sha256())).toList();
    }

    private FileFact persistedFileFact(SatisfactionResponseFileDO row) {
        return new FileFact(row.getFileRole(), row.getReferenceKey(), row.getFileSequence(), row.getArtifactId(),
                row.getVersionNo(), row.getReferenceKey(), row.getArtifactVersion(), row.getReferenceVersion(),
                row.getAvailabilityVersion(), row.getScopeVersion(), row.getFileHash());
    }

    private List<FileFact> stableFiles(List<FileFact> files, boolean includeServerSlot) {
        return files.stream().map(file -> includeServerSlot ? file : new FileFact(file.role(), file.referenceKey(),
                        file.sequence(), file.artifactId(), file.versionNo(), file.referenceKey(),
                        file.artifactVersion(), file.referenceVersion(), file.availabilityVersion(),
                        file.scopeVersion(), file.sha256()))
                .sorted(Comparator.comparing(FileFact::role)
                .thenComparing(FileFact::sequence)).toList();
    }

    private String policyKey(String role) {
        return "SIGNATURE".equals(role) ? "SATISFACTION_SIGNATURE" : "SATISFACTION_ATTACHMENT";
    }

    private String role(String policyKey) {
        return "SATISFACTION_SIGNATURE".equals(policyKey) ? "SIGNATURE" : "ATTACHMENT";
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Command(Long tenantId, String token, String requestId, Long reservedResponseId,
                          String submitChannel,
                          String customerContactRef, Long assistedByUserId, String answerSnapshot,
                          List<FileFact> files, String actorRef) {
        public Command(Long tenantId, String token, String requestId, String submitChannel,
                       String customerContactRef, Long assistedByUserId, String answerSnapshot,
                       List<FileFact> files, String actorRef) {
            this(tenantId, token, requestId, null, submitChannel, customerContactRef,
                    assistedByUserId, answerSnapshot, files, actorRef);
        }
    }
    public record FileFact(String role, String fileSlotKey, Integer sequence, Long artifactId, Integer versionNo,
                           String referenceKey, Integer artifactVersion, Integer referenceVersion,
                           Integer availabilityVersion, Long scopeVersion, String sha256) {
        public FileFact(String role, Integer sequence, Long artifactId, Integer versionNo,
                        String referenceKey, Integer artifactVersion, Integer referenceVersion,
                        Integer availabilityVersion, Long scopeVersion, String sha256) {
            this(role, null, sequence, artifactId, versionNo, referenceKey, artifactVersion,
                    referenceVersion, availabilityVersion, scopeVersion, sha256);
        }
    }
    public record SubmissionResult(Long responseId, Long questionnaireId, Long taskId, boolean replayed,
                                   BigDecimal score, BigDecimal threshold, boolean passed, String ruleVersion) {}
    public record AssistedFile(String role, Integer sequence, FileArtifactVersionRevalidationQuery source) {}
    private record AttachedAssistedFile(String role, Integer sequence, FileArtifactVersionFact fact) {}
    public record AssistedCommand(Long tenantId, Long actorUserId, Long taskId, String requestId,
                                  String customerContactRef, String answerSnapshot, List<AssistedFile> files) {}
}
