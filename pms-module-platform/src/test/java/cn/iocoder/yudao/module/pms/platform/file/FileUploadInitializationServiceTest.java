package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.pms.platform.service.file.BoundedMultipartReader;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadInitializationServiceTest {

    @Mock FileUploadSessionMapper sessionMapper;
    @Mock FileArtifactMapper artifactMapper;
    @Mock FileVersionMapper versionMapper;
    @Mock FileReferenceMapper referenceMapper;
    @Mock FileBusinessObjectPolicyRegistry policyRegistry;
    @Mock BoundedMultipartReader multipartReader;
    @Mock FileContentPolicyService contentPolicyService;
    @Mock FileStorageReceiptApi storageReceiptApi;
    @Mock FileEventFactory eventFactory;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;

    private FileUploadApplicationService service;
    private final AtomicReference<PlatformCommandExecutionApi.SuccessFacts> successFacts =
            new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new FileUploadApplicationService(
                sessionMapper, artifactMapper, versionMapper, referenceMapper, policyRegistry,
                multipartReader, contentPolicyService, storageReceiptApi, eventFactory,
                commandExecutionApi, operationAuditApi,
                Duration.ofMinutes(15));
    }

    @Test
    void initializesTrustedSessionWithoutCreatingFileVersion() {
        when(policyRegistry.inspect(any())).thenReturn(policy("MUTABLE", 52_428_800L));
        when(sessionMapper.insert(any())).thenReturn(1);
        executeImmediately();

        FileUploadInitialized result = service.initialize(command(
                "CREATE_ARTIFACT", null, null, 1024L, "application/pdf"));

        assertNotNull(result.artifactId());
        assertNotNull(result.sessionId());
        ArgumentCaptor<FileUploadSessionDO> captor = ArgumentCaptor.forClass(FileUploadSessionDO.class);
        verify(sessionMapper).insert(captor.capture());
        FileUploadSessionDO row = captor.getValue();
        assertEquals(result.sessionId(), row.getId());
        assertEquals(String.valueOf(result.sessionId()), row.getStorageOperationId());
        assertEquals(result.artifactId(), row.getArtifactId());
        assertEquals("INITIALIZED", row.getStatusCode());
        assertEquals(8L, row.getScopeVersion());
        Map<?, ?> audit = JsonUtils.parseObject(successFacts.get().detailSnapshot(), Map.class);
        assertEquals("evidence.pdf", audit.get("fileName"));
        assertEquals(1024, audit.get("declaredSizeBytes"));
        assertEquals("application/pdf", audit.get("declaredMediaType"));
        assertEquals("NONE", audit.get("expectedReferenceVersion"));
        assertEquals("idem-1", audit.get("operationId"));
        assertEquals(String.valueOf(result.sessionId()), audit.get("storageOperationId"));
        assertEquals("NONE", audit.get("statusBefore"));
        assertEquals("INITIALIZED", audit.get("statusAfter"));
        assertEquals("NONE", audit.get("versionBefore"));
        assertEquals(0, audit.get("versionAfter"));
    }

    @Test
    void rejectsDeclaredSizeAboveBusinessLimitWithoutSessionSideEffect() {
        when(policyRegistry.inspect(any())).thenReturn(policy("MUTABLE", 100L));
        executeImmediately();

        assertThrows(RuntimeException.class, () -> service.initialize(command(
                "CREATE_ARTIFACT", null, null, 101L, "application/pdf")));

        verify(sessionMapper, never()).insert(any());
        ArgumentCaptor<Map<String, ?>> audit = rejectedAudit();
        assertEquals(101L, audit.getValue().get("declaredSizeBytes"));
        assertEquals("REJECTED", audit.getValue().get("statusAfter"));
        assertEquals("idem-1", audit.getValue().get("operationId"));
        assertNotNull(audit.getValue().get("failureCode"));
    }

    @Test
    void rejectsReplacementForImmutableReference() {
        when(policyRegistry.inspect(any())).thenReturn(policy("IMMUTABLE", 52_428_800L));
        executeImmediately();

        assertThrows(RuntimeException.class, () -> service.initialize(command(
                "ADD_VERSION", 9001L, 2, 1024L, "application/pdf")));

        verify(sessionMapper, never()).insert(any());
    }

    @Test
    void rejectsIdempotencyConflictWithoutSessionSideEffect() {
        when(commandExecutionApi.execute(any(), anyString(), eq(FileUploadInitialized.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        assertThrows(RuntimeException.class, () -> service.initialize(command(
                "CREATE_ARTIFACT", null, null, 1024L, "application/pdf")));

        verify(sessionMapper, never()).insert(any());
        verify(policyRegistry, never()).inspect(any());
        ArgumentCaptor<Map<String, ?>> audit = rejectedAudit();
        assertEquals("NONE", audit.getValue().get("expectedReferenceVersion"));
        assertEquals("NONE", audit.getValue().get("versionAfter"));
    }

    @Test
    void replaysCompletedInitializationWithoutReauthorizingCurrentScope() {
        FileUploadInitialized completed = new FileUploadInitialized(
                9001L, 8001L, java.time.LocalDateTime.now().plusMinutes(5));
        when(commandExecutionApi.execute(any(), anyString(), eq(FileUploadInitialized.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, completed));

        FileUploadInitialized result = service.initialize(command(
                "CREATE_ARTIFACT", null, null, 1024L, "application/pdf"));

        assertEquals(completed, result);
        verify(policyRegistry, never()).inspect(any());
        verify(sessionMapper, never()).insert(any());
    }

    @SuppressWarnings("unchecked")
    private void executeImmediately() {
        when(commandExecutionApi.execute(any(), anyString(), eq(FileUploadInitialized.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<FileUploadInitialized> operation = invocation.getArgument(3);
                    Function<FileUploadInitialized, PlatformCommandExecutionApi.SuccessFacts> factsFactory =
                            invocation.getArgument(4);
                    FileUploadInitialized result = operation.get();
                    successFacts.set(factsFactory.apply(result));
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, ?>> rejectedAudit() {
        ArgumentCaptor<Map<String, ?>> detail = ArgumentCaptor.forClass((Class) Map.class);
        verify(operationAuditApi).record(eq(0L), eq(7L), eq("idem-1"),
                eq("FILE_UPLOAD_INITIALIZE"), eq("FileUploadSession"), eq("UNKNOWN"),
                eq("REJECTED"), detail.capture());
        return detail;
    }

    private FileUploadInitializeCommand command(String mode, Long artifactId,
                                                Integer expectedReferenceVersion,
                                                long size, String mediaType) {
        return new FileUploadInitializeCommand(
                0L, 7L, "idem-1", mode, artifactId, expectedReferenceVersion,
                "SOL", "CONSTRUCTION_PLAN_CHANGE", "1001", "CUSTOMER_DELAY_EVIDENCE",
                "delay-evidence-1", "evidence.pdf", "CUSTOMER_DELAY_EVIDENCE",
                size, mediaType, null);
    }

    private FileBusinessObjectPolicyFact policy(String mutability, long maxBytes) {
        return new FileBusinessObjectPolicyFact(
                true, 8L, mutability, "SINGLE", Set.of("CUSTOMER_DELAY_EVIDENCE"),
                Set.of("application/pdf"), maxBytes, "INTERNAL");
    }
}
