package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.BusinessGrantUploadSessionQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleted;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BusinessGrantFileUploadService {
    private static final String OWNER = "ACC";
    private static final String OBJECT = "SATISFACTION_RESPONSE";
    private static final Set<String> POLICIES = Set.of("SATISFACTION_SIGNATURE", "SATISFACTION_ATTACHMENT");

    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final FileUploadApplicationService uploadService;
    private final FileUploadSessionMapper sessionMapper;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final OperationAuditApi operationAuditApi;

    @Transactional(rollbackFor = Exception.class)
    public BusinessGrantUploadInitialized initialize(BusinessGrantUploadInitializeCommand command) {
        requireInitialize(command);
        policyRegistry.initializeBusinessGrantUploadPolicy(
                new BusinessGrantUploadInitializePolicyQuery(command.tenantId(), command.grantId(),
                        command.grantVersion(), command.questionnaireId(), command.requestId(),
                        command.responseId(), command.policyKey(), "ALLOCATING:" + command.operationId(),
                        "SATISFACTION_SIGNATURE".equals(command.policyKey()) ? 1 : 2));
        SlotIdentity slot = allocateSlot(command);
        String fileSlotKey = slot.fileSlotKey();
        int sequence = slot.fileSequence();
        BusinessGrantUploadPolicyFact policy = policyRegistry.initializeBusinessGrantUploadPolicy(
                new BusinessGrantUploadInitializePolicyQuery(command.tenantId(), command.grantId(),
                        command.grantVersion(), command.questionnaireId(), command.requestId(),
                        command.responseId(), command.policyKey(), fileSlotKey, sequence));
        if (!command.policyKey().equals(command.categoryCode())) {
            throw new IllegalArgumentException("BUSINESS_GRANT_FILE_CATEGORY_INVALID");
        }
        FileUploadInitialized initialized = uploadService.initializeAuthorized(new FileUploadInitializeCommand(
                command.tenantId(), policy.grantIssuerUserId(), command.operationId(),
                FileUploadApplicationService.MODE_CREATE_ARTIFACT, null, null, OWNER, OBJECT,
                String.valueOf(command.responseId()), command.policyKey(), fileSlotKey, command.fileName(),
                command.categoryCode(), command.declaredSizeBytes(), command.declaredMediaType(),
                command.clientSha256()), policy.filePolicy());
        audit(command.tenantId(), policy.grantIssuerUserId(), command.operationId(), "INITIALIZED",
                command.grantId(), command.grantVersion(), command.questionnaireId(), command.responseId(),
                command.policyKey(), fileSlotKey, sequence, initialized.artifactId());
        return new BusinessGrantUploadInitialized(command.responseId(), fileSlotKey, sequence,
                initialized.artifactId(), initialized.sessionId(), policy.scopeVersion(), initialized.expiresAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public BusinessGrantFileFact complete(BusinessGrantUploadCompleteCommand command) {
        requireComplete(command);
        FileUploadSessionDO session = sessionMapper.selectForUpdate(
                new FileUploadSessionLockQuery(command.tenantId(), command.sessionId()));
        requireSession(command, session);
        BusinessGrantUploadPolicyFact policy = policyRegistry.lockAndRevalidateBusinessGrantUpload(
                new BusinessGrantUploadCompletePolicyQuery(command.tenantId(), command.grantId(),
                        command.grantVersion(), command.questionnaireId(), command.requestId(),
                        command.responseId(), command.policyKey(), command.fileSlotKey(),
                        command.fileSequence(), session.getScopeVersion()));
        FileUploadCompleted completed = uploadService.completeAuthorized(new FileUploadCompleteCommand(
                command.tenantId(), policy.grantIssuerUserId(), command.operationId(), command.artifactId(),
                command.sessionId(), null, command.clientSha256()), command.content(), policy.filePolicy());
        BusinessGrantFileFact fact = fact(command.tenantId(), command.responseId(), policy.scopeVersion(), command.policyKey(),
                command.fileSlotKey(), command.fileSequence(), completed.artifactId(), completed.versionNo(),
                completed.referenceKey());
        audit(command.tenantId(), policy.grantIssuerUserId(), command.operationId(), "COMPLETED",
                command.grantId(), command.grantVersion(), command.questionnaireId(), command.responseId(),
                command.policyKey(), command.fileSlotKey(), command.fileSequence(), completed.artifactId());
        return fact;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<BusinessGrantFileFact> lockAndRevalidate(BusinessGrantFilesRevalidationCommand command) {
        requireRevalidation(command);
        policyRegistry.lockAndRevalidateBusinessGrantFiles(new BusinessGrantFileRevalidationQuery(
                command.tenantId(), command.grantId(), command.grantVersion(), command.questionnaireId(),
                command.requestId(), command.responseId(), command.files()));
        List<BusinessGrantFileFact> facts = new ArrayList<>(command.files().size());
        for (BusinessGrantFileHandle handle : command.files().stream()
                .sorted(Comparator.comparing(BusinessGrantFileHandle::policyKey)
                        .thenComparing(BusinessGrantFileHandle::fileSequence)).toList()) {
            BusinessGrantFileFact actual = fact(command.tenantId(), command.responseId(), handle.scopeVersion(), handle.policyKey(),
                    handle.fileSlotKey(), handle.fileSequence(), handle.artifactId(), handle.versionNo(),
                    handle.referenceKey());
            FileArtifactVersionFact file = actual.fileFact();
            if (!handle.artifactVersion().equals(file.fileFactVersion().artifactVersion())
                    || !handle.referenceVersion().equals(file.fileFactVersion().referenceVersion())
                    || !handle.availabilityVersion().equals(file.fileFactVersion().availabilityVersion())
                    || !handle.sha256().equals(file.sha256())) {
                throw new IllegalStateException("BUSINESS_GRANT_FILE_FACT_CONFLICT");
            }
            facts.add(actual);
        }
        return List.copyOf(facts);
    }

    private BusinessGrantFileFact fact(Long tenantId, Long responseId, Long scopeVersion, String policyKey,
                                       String fileSlotKey, Integer sequence, Long artifactId,
                                       Integer versionNo, String referenceKey) {
        String referenceObjectId = referenceObjectId(referenceKey);
        if (!String.valueOf(responseId).equals(referenceObjectId) || !fileSlotKey.equals(referenceKey)) {
            throw new IllegalStateException("BUSINESS_GRANT_FILE_REFERENCE_INVALID");
        }
        FileReferenceDO reference = referenceMapper.selectForUpdate(new FileReferenceLockQuery(
                tenantId, OWNER, OBJECT, referenceObjectId, policyKey, referenceKey));
        FileArtifactDO artifact = artifactMapper.selectForUpdate(new FileArtifactLockQuery(tenantId, artifactId));
        FileVersionDO version = versionMapper.selectForUpdate(new FileVersionLockQuery(
                tenantId, artifactId, versionNo));
        if (reference == null || artifact == null || version == null
                || !artifactId.equals(reference.getArtifactId())
                || !versionNo.equals(reference.getFileVersionNo())
                || !scopeVersion.equals(reference.getScopeVersion())
                || !OWNER.equals(artifact.getOwnerContext()) || !policyKey.equals(artifact.getCategoryCode())
                || !"ACTIVE".equals(artifact.getLifecycleStatusCode())
                || !"ACTIVE".equals(reference.getStatusCode())
                || !"AVAILABLE".equals(version.getAvailabilityStatusCode())) {
            throw new IllegalStateException("BUSINESS_GRANT_FILE_FACT_CONFLICT");
        }
        FileArtifactVersionFact fileFact = new FileArtifactVersionFact(artifactId, versionNo, referenceKey,
                artifact.getCategoryCode(), artifact.getName(), version.getSizeBytes(),
                version.getDetectedMediaType(), version.getSha256(), version.getAvailabilityStatusCode(),
                reference.getStatusCode(), new FileFactVersion(artifact.getVersion(), reference.getVersion(),
                version.getAvailabilityVersion()), scopeVersion);
        return new BusinessGrantFileFact(policyKey, fileSlotKey, sequence, fileFact);
    }

    private String referenceObjectId(String referenceKey) {
        String[] parts = referenceKey.split(":", 3);
        if (parts.length != 3 || !"grant-file".equals(parts[0])) {
            throw new IllegalStateException("BUSINESS_GRANT_FILE_REFERENCE_INVALID");
        }
        return parts[1];
    }

    private void requireSession(BusinessGrantUploadCompleteCommand command, FileUploadSessionDO session) {
        if (session == null || !command.artifactId().equals(session.getArtifactId())
                || !OWNER.equals(session.getOwnerContext()) || !OBJECT.equals(session.getObjectType())
                || !String.valueOf(command.responseId()).equals(session.getObjectId())
                || !command.policyKey().equals(session.getPurposeCode())
                || !command.fileSlotKey().equals(session.getReferenceKey())) {
            throw new IllegalStateException("BUSINESS_GRANT_FILE_SESSION_CONFLICT");
        }
    }

    private void requireInitialize(BusinessGrantUploadInitializeCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.grantId() == null || command.grantVersion() == null
                || command.questionnaireId() == null || command.responseId() == null
                || blank(command.requestId()) || !POLICIES.contains(command.policyKey())
                || blank(command.operationId()) || command.operationId().length() > 64
                || blank(command.fileName()) || blank(command.categoryCode())
                || command.declaredSizeBytes() == null || command.declaredSizeBytes() <= 0
                || blank(command.declaredMediaType())) {
            throw new IllegalArgumentException("BUSINESS_GRANT_UPLOAD_INITIALIZE_INVALID");
        }
    }

    private void requireComplete(BusinessGrantUploadCompleteCommand command) {
        if (command == null || command.content() == null || command.content().length == 0
                || blank(command.requestId()) || !POLICIES.contains(command.policyKey())
                || blank(command.operationId()) || blank(command.fileSlotKey())
                || command.fileSequence() == null || command.fileSequence() <= 0
                || command.artifactId() == null || command.sessionId() == null) {
            throw new IllegalArgumentException("BUSINESS_GRANT_UPLOAD_COMPLETE_INVALID");
        }
    }

    private void requireRevalidation(BusinessGrantFilesRevalidationCommand command) {
        if (command == null || command.files().isEmpty() || blank(command.requestId())
                || command.responseId() == null || command.files().stream().anyMatch(handle -> handle == null
                || !POLICIES.contains(handle.policyKey()) || blank(handle.fileSlotKey())
                || handle.fileSequence() == null || handle.fileSequence() <= 0
                || handle.artifactId() == null || handle.versionNo() == null || blank(handle.referenceKey())
                || handle.artifactVersion() == null || handle.referenceVersion() == null
                || handle.availabilityVersion() == null || handle.scopeVersion() == null
                || blank(handle.sha256()))) {
            throw new IllegalArgumentException("BUSINESS_GRANT_FILE_REVALIDATION_INVALID");
        }
    }

    private SlotIdentity allocateSlot(BusinessGrantUploadInitializeCommand command) {
        List<FileUploadSessionDO> sessions = sessionMapper.selectBusinessGrantSlotsForUpdate(
                new BusinessGrantUploadSessionQuery(command.tenantId(), OWNER, OBJECT,
                        String.valueOf(command.responseId())));
        String suffix = ":" + command.operationId().trim();
        for (FileUploadSessionDO session : sessions) {
            if (command.policyKey().equals(session.getPurposeCode())
                    && session.getReferenceKey().endsWith(suffix)) {
                return new SlotIdentity(session.getReferenceKey(), slotSequence(session.getReferenceKey()));
            }
        }
        if ("SATISFACTION_SIGNATURE".equals(command.policyKey())) {
            if (sessions.stream().anyMatch(session -> "SATISFACTION_SIGNATURE".equals(session.getPurposeCode()))) {
                throw new IllegalStateException("BUSINESS_GRANT_SIGNATURE_SLOT_OCCUPIED");
            }
            return slot(command, 1);
        }
        int next = sessions.stream().filter(session -> "SATISFACTION_ATTACHMENT".equals(session.getPurposeCode()))
                .mapToInt(session -> slotSequence(session.getReferenceKey())).max().orElse(1) + 1;
        return slot(command, next);
    }

    private SlotIdentity slot(BusinessGrantUploadInitializeCommand command, int sequence) {
        return new SlotIdentity("grant-file:" + command.responseId() + ":" + sequence + ":"
                + command.operationId().trim(), sequence);
    }

    private int slotSequence(String referenceKey) {
        String[] parts = referenceKey.split(":", 5);
        if (parts.length < 4 || !"grant-file".equals(parts[0])) {
            throw new IllegalStateException("BUSINESS_GRANT_FILE_REFERENCE_INVALID");
        }
        try {
            int parsed = Integer.parseInt(parts[2]);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("BUSINESS_GRANT_FILE_REFERENCE_INVALID");
        }
    }

    private void audit(Long tenantId, Long actorId, String operationId, String status,
                       Long grantId, Integer grantVersion, Long questionnaireId, Long responseId,
                       String policyKey, String fileSlotKey, Integer sequence, Long artifactId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("subjectType", "BUSINESS_GRANT");
        detail.put("grantId", grantId);
        detail.put("grantVersion", grantVersion);
        detail.put("questionnaireId", questionnaireId);
        detail.put("responseId", responseId);
        detail.put("policyKey", policyKey);
        detail.put("fileSlotKey", fileSlotKey);
        detail.put("fileSequence", sequence);
        detail.put("artifactId", artifactId);
        operationAuditApi.record(tenantId, actorId, operationId, "BUSINESS_GRANT_FILE_UPLOAD",
                "SatisfactionResponse", String.valueOf(responseId), status, Map.copyOf(detail));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record SlotIdentity(String fileSlotKey, Integer fileSequence) {
    }
}
