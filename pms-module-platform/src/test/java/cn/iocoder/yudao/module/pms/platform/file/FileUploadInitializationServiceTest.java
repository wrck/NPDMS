package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Set;
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
    @Mock FileBusinessObjectPolicyRegistry policyRegistry;
    @Mock PlatformCommandExecutionApi commandExecutionApi;

    private FileUploadApplicationService service;

    @BeforeEach
    void setUp() {
        service = new FileUploadApplicationService(
                sessionMapper, policyRegistry, commandExecutionApi, Duration.ofMinutes(15));
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
    }

    @Test
    void rejectsDeclaredSizeAboveBusinessLimitWithoutSessionSideEffect() {
        when(policyRegistry.inspect(any())).thenReturn(policy("MUTABLE", 100L));
        executeImmediately();

        assertThrows(RuntimeException.class, () -> service.initialize(command(
                "CREATE_ARTIFACT", null, null, 101L, "application/pdf")));

        verify(sessionMapper, never()).insert(any());
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
                    factsFactory.apply(result);
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });
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
