package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFileCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFilePolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService;
import cn.iocoder.yudao.module.pms.platform.service.file.GeneratedBusinessFileService;
import cn.iocoder.yudao.module.pms.platform.service.file.GeneratedBusinessFileTransactionService;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.platform.service.file.command.GeneratedBusinessFileReservation;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileReferenceAttachedMessage;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileVersionCommittedMessage;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneratedBusinessFileServiceTest {

    @Mock GeneratedBusinessFileTransactionService transactions;
    @Mock FileBusinessObjectPolicyRegistry policyRegistry;
    @Mock FileContentPolicyService contentPolicyService;
    @Mock FileUploadSessionMapper sessionMapper;
    @Mock FileArtifactMapper artifactMapper;
    @Mock FileVersionMapper versionMapper;
    @Mock FileReferenceMapper referenceMapper;
    @Mock PermissionApi permissionApi;
    private FileEventFactory eventFactory;
    @Mock PlatformTransactionalOutboxWriter outboxWriter;
    private GeneratedBusinessFileService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        eventFactory = new FileEventFactory();
        service = new GeneratedBusinessFileService(transactions, policyRegistry, contentPolicyService,
                sessionMapper, artifactMapper, versionMapper, referenceMapper, permissionApi,
                eventFactory, outboxWriter);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TenantContextHolder.clear();
    }

    @Test
    void createsOneGeneratedFactAndCompletesSessionAfterOuterCommit() {
        GeneratedBusinessFileCommand command = command();
        FileBusinessObjectPolicyFact policy = new FileBusinessObjectPolicyFact(true, 9L,
                "IMMUTABLE", "SINGLE", Set.of("SATISFACTION_RESULT_DOCUMENT"),
                Set.of("application/pdf"), 5_242_880L, "INTERNAL");
        ValidatedFileContent content = new ValidatedFileContent(new byte[]{1, 2, 3}, 3,
                "a".repeat(64), "application/pdf", "pdf", "SKIPPED", null, null);
        GeneratedBusinessFileReservation reservation = new GeneratedBusinessFileReservation(50L, 60L, "50");
        FileStorageReceipt receipt = new FileStorageReceipt("50", 70L, "result.pdf", "application/pdf", 3L);
        FileUploadSessionDO session = new FileUploadSessionDO();
        session.setId(50L); session.setStatusCode("INITIALIZED"); session.setArtifactId(60L);
        session.setRegisteredInfraFileId(70L); session.setActualSha256("a".repeat(64));

        when(permissionApi.hasAnyPermissions(30L, "pms:file:upload")).thenReturn(true);
        when(policyRegistry.lockAndRevalidateGeneratedBusinessFile(any())).thenReturn(policy);
        when(contentPolicyService.validateBounded(any())).thenReturn(content);
        when(transactions.reserve(eq(command), eq(content), anyString())).thenReturn(reservation);
        when(transactions.store(command, reservation, content)).thenReturn(receipt);
        when(sessionMapper.selectForUpdate(any())).thenReturn(session);
        when(referenceMapper.selectForUpdate(any())).thenReturn(null);
        when(artifactMapper.selectForUpdate(any())).thenReturn(null);
        when(artifactMapper.insert(any())).thenReturn(1);
        when(versionMapper.insert(any())).thenReturn(1);
        when(referenceMapper.insert(any())).thenAnswer(invocation -> {
            FileReferenceDO row = invocation.getArgument(0);
            row.setId(80L);
            return 1;
        });
        when(artifactMapper.activateDraftIfMatch(any())).thenReturn(1);

        var fact = service.create(command);
        assertEquals(60L, fact.artifactId());
        assertEquals("a".repeat(64), fact.sha256());
        ArgumentCaptor<GeneratedBusinessFilePolicyRevalidationQuery> policyQuery =
                ArgumentCaptor.forClass(GeneratedBusinessFilePolicyRevalidationQuery.class);
        verify(policyRegistry).lockAndRevalidateGeneratedBusinessFile(policyQuery.capture());
        assertEquals(10L, policyQuery.getValue().collectionTaskId());
        assertEquals(11L, policyQuery.getValue().questionnaireId());
        assertEquals(12L, policyQuery.getValue().responseId());
        assertEquals(4L, policyQuery.getValue().expectedTaskVersion());
        ArgumentCaptor<cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent>
                eventCaptor = ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent.class);
        verify(outboxWriter, times(2)).write(eq(7L), eventCaptor.capture(), eq("FileArtifact"), eq("60"), any());
        assertEquals(Set.of(FileEventFactory.VERSION_COMMITTED, FileEventFactory.REFERENCE_ATTACHED),
                eventCaptor.getAllValues().stream().map(event -> event.eventType()).collect(java.util.stream.Collectors.toSet()));
        var versionEvent = eventCaptor.getAllValues().stream()
                .filter(event -> FileEventFactory.VERSION_COMMITTED.equals(event.eventType())).findFirst().orElseThrow();
        var versionMessage = JsonUtils.parseObject(versionEvent.eventPayload(), FileVersionCommittedMessage.class);
        assertEquals(7L, versionMessage.tenantId());
        assertEquals(60L, versionMessage.artifactId());
        assertEquals(1, versionMessage.versionNo());
        assertEquals("a".repeat(64), versionMessage.sha256());
        assertEquals("SKIPPED", versionMessage.scanStatus());
        assertEquals("result-op-1", versionMessage.operationId());
        var referenceEvent = eventCaptor.getAllValues().stream()
                .filter(event -> FileEventFactory.REFERENCE_ATTACHED.equals(event.eventType())).findFirst().orElseThrow();
        var referenceMessage = JsonUtils.parseObject(referenceEvent.eventPayload(), FileReferenceAttachedMessage.class);
        assertEquals(7L, referenceMessage.tenantId());
        assertEquals(80L, referenceMessage.referenceId());
        assertEquals(60L, referenceMessage.artifactId());
        assertEquals(1, referenceMessage.versionNo());
        assertEquals("ACC", referenceMessage.ownerContext());
        assertEquals("SATISFACTION_RESULT", referenceMessage.objectType());
        assertEquals("40", referenceMessage.objectId());
        assertEquals("SATISFACTION_RESULT_DOCUMENT", referenceMessage.purposeCode());
        assertEquals("result-op-1", referenceMessage.operationId());

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(transactions).completeSession(eq(7L), eq(50L), any(), eq(receipt));
    }

    @Test
    void doesNotAppendEventsWhenPersistenceFails() {
        GeneratedBusinessFileCommand command = command();
        FileBusinessObjectPolicyFact policy = new FileBusinessObjectPolicyFact(true, 9L,
                "IMMUTABLE", "SINGLE", Set.of("SATISFACTION_RESULT_DOCUMENT"),
                Set.of("application/pdf"), 5_242_880L, "INTERNAL");
        ValidatedFileContent content = new ValidatedFileContent(new byte[]{1, 2, 3}, 3,
                "a".repeat(64), "application/pdf", "pdf", "SKIPPED", null, null);
        GeneratedBusinessFileReservation reservation = new GeneratedBusinessFileReservation(50L, 60L, "50");
        FileStorageReceipt receipt = new FileStorageReceipt("50", 70L, "result.pdf", "application/pdf", 3L);
        FileUploadSessionDO session = new FileUploadSessionDO();
        session.setId(50L); session.setStatusCode("INITIALIZED"); session.setArtifactId(60L);
        session.setRegisteredInfraFileId(70L); session.setActualSha256("a".repeat(64));

        when(permissionApi.hasAnyPermissions(30L, "pms:file:upload")).thenReturn(true);
        when(policyRegistry.lockAndRevalidateGeneratedBusinessFile(any())).thenReturn(policy);
        when(contentPolicyService.validateBounded(any())).thenReturn(content);
        when(transactions.reserve(eq(command), eq(content), anyString())).thenReturn(reservation);
        when(transactions.store(command, reservation, content)).thenReturn(receipt);
        when(sessionMapper.selectForUpdate(any())).thenReturn(session);
        when(referenceMapper.selectForUpdate(any())).thenReturn(null);
        when(artifactMapper.selectForUpdate(any())).thenReturn(null);
        when(artifactMapper.insert(any())).thenReturn(1);
        when(versionMapper.insert(any())).thenReturn(1);
        when(referenceMapper.insert(any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.create(command));
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void outboxFailurePropagatesBeforeSessionCompletionRegistration() {
        GeneratedBusinessFileCommand command = command();
        FileBusinessObjectPolicyFact policy = new FileBusinessObjectPolicyFact(true, 9L,
                "IMMUTABLE", "SINGLE", Set.of("SATISFACTION_RESULT_DOCUMENT"),
                Set.of("application/pdf"), 5_242_880L, "INTERNAL");
        ValidatedFileContent content = new ValidatedFileContent(new byte[]{1, 2, 3}, 3,
                "a".repeat(64), "application/pdf", "pdf", "SKIPPED", null, null);
        GeneratedBusinessFileReservation reservation = new GeneratedBusinessFileReservation(50L, 60L, "50");
        FileStorageReceipt receipt = new FileStorageReceipt("50", 70L, "result.pdf", "application/pdf", 3L);
        FileUploadSessionDO session = new FileUploadSessionDO();
        session.setId(50L); session.setStatusCode("INITIALIZED"); session.setArtifactId(60L);
        session.setRegisteredInfraFileId(70L); session.setActualSha256("a".repeat(64));

        when(permissionApi.hasAnyPermissions(30L, "pms:file:upload")).thenReturn(true);
        when(policyRegistry.lockAndRevalidateGeneratedBusinessFile(any())).thenReturn(policy);
        when(contentPolicyService.validateBounded(any())).thenReturn(content);
        when(transactions.reserve(eq(command), eq(content), anyString())).thenReturn(reservation);
        when(transactions.store(command, reservation, content)).thenReturn(receipt);
        when(sessionMapper.selectForUpdate(any())).thenReturn(session);
        when(referenceMapper.selectForUpdate(any())).thenReturn(null);
        when(artifactMapper.selectForUpdate(any())).thenReturn(null);
        when(artifactMapper.insert(any())).thenReturn(1);
        when(versionMapper.insert(any())).thenReturn(1);
        when(referenceMapper.insert(any())).thenAnswer(invocation -> {
            FileReferenceDO row = invocation.getArgument(0);
            row.setId(80L);
            return 1;
        });
        when(artifactMapper.activateDraftIfMatch(any())).thenReturn(1);
        doThrow(new IllegalStateException("OUTBOX_WRITE_FAILED")).when(outboxWriter)
                .write(eq(7L), any(), eq("FileArtifact"), eq("60"), any());

        assertThrows(IllegalStateException.class, () -> service.create(command));
        verify(transactions, never()).completeSession(anyLong(), anyLong(), any(), any());
        verify(outboxWriter).write(eq(7L), any(), eq("FileArtifact"), eq("60"), any());
    }

    @Test
    void idempotentReplayDoesNotAppendNewEvents() {
        GeneratedBusinessFileCommand command = command();
        FileBusinessObjectPolicyFact policy = new FileBusinessObjectPolicyFact(true, 9L,
                "IMMUTABLE", "SINGLE", Set.of("SATISFACTION_RESULT_DOCUMENT"),
                Set.of("application/pdf"), 5_242_880L, "INTERNAL");
        ValidatedFileContent content = new ValidatedFileContent(new byte[]{1, 2, 3}, 3,
                "a".repeat(64), "application/pdf", "pdf", "SKIPPED", null, null);
        GeneratedBusinessFileReservation reservation = new GeneratedBusinessFileReservation(50L, 60L, "50");
        FileStorageReceipt receipt = new FileStorageReceipt("50", 70L, "result.pdf", "application/pdf", 3L);
        FileUploadSessionDO session = new FileUploadSessionDO();
        session.setId(50L); session.setStatusCode("INITIALIZED"); session.setArtifactId(60L);
        session.setRegisteredInfraFileId(70L); session.setActualSha256("a".repeat(64));
        FileReferenceDO existing = new FileReferenceDO();
        existing.setId(80L); existing.setArtifactId(60L); existing.setFileVersionNo(1);
        existing.setStatusCode("ACTIVE"); existing.setScopeVersion(9L); existing.setReferenceKey("satisfaction-result-40");
        existing.setVersion(0);
        FileArtifactDO artifact = new FileArtifactDO(); artifact.setId(60L); artifact.setLifecycleStatusCode("ACTIVE");
        artifact.setCategoryCode("SATISFACTION_RESULT_DOCUMENT"); artifact.setName("result.pdf"); artifact.setVersion(1);
        FileVersionDO version = new FileVersionDO(); version.setVersionNo(1); version.setSizeBytes(3L);
        version.setDetectedMediaType("application/pdf"); version.setSha256("a".repeat(64));
        version.setAvailabilityStatusCode("AVAILABLE"); version.setAvailabilityVersion(0);

        when(permissionApi.hasAnyPermissions(30L, "pms:file:upload")).thenReturn(true);
        when(policyRegistry.lockAndRevalidateGeneratedBusinessFile(any())).thenReturn(policy);
        when(contentPolicyService.validateBounded(any())).thenReturn(content);
        when(transactions.reserve(eq(command), eq(content), anyString())).thenReturn(reservation);
        when(transactions.store(command, reservation, content)).thenReturn(receipt);
        when(sessionMapper.selectForUpdate(any())).thenReturn(session);
        when(referenceMapper.selectForUpdate(any())).thenReturn(existing);
        when(artifactMapper.selectForUpdate(any())).thenReturn(artifact);
        when(versionMapper.selectForUpdate(any())).thenReturn(version);

        service.create(command);
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void rejectsMissingUploadPermissionBeforeOwnerOrStorage() {
        when(permissionApi.hasAnyPermissions(30L, "pms:file:upload")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.create(command()));
        verifyNoInteractions(policyRegistry, contentPolicyService, transactions);
    }

    private GeneratedBusinessFileCommand command() {
        return new GeneratedBusinessFileCommand(7L, 30L, "result-op-1", 40L, 10L, 11L, 12L, 4L,
                "ACC", "SATISFACTION_RESULT", "SATISFACTION_RESULT_DOCUMENT", 9L,
                "result.pdf", "application/pdf", new byte[]{1, 2, 3});
    }
}
