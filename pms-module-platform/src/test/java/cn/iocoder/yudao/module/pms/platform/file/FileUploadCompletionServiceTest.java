package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.BoundedMultipartReader;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleted;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadCompletionServiceTest {

    @Mock FileUploadSessionMapper sessionMapper;
    @Mock FileArtifactMapper artifactMapper;
    @Mock FileVersionMapper versionMapper;
    @Mock FileReferenceMapper referenceMapper;
    @Mock FileBusinessObjectPolicyRegistry policyRegistry;
    @Mock BoundedMultipartReader multipartReader;
    @Mock FileContentPolicyService contentPolicyService;
    @Mock FileStorageReceiptApi storageReceiptApi;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;

    private FileUploadApplicationService service;
    private final AtomicReference<PlatformCommandExecutionApi.SuccessFacts> successFacts = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new FileUploadApplicationService(sessionMapper, artifactMapper, versionMapper, referenceMapper,
                policyRegistry, multipartReader, contentPolicyService, storageReceiptApi, new FileEventFactory(),
                commandExecutionApi, operationAuditApi, Duration.ofMinutes(15));
    }

    @Test
    void completesFirstUploadAsOneArtifactVersionReferenceAndTwoEvents() {
        byte[] bytes = "%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String sha = "a".repeat(64);
        FileUploadSessionDO session = session(bytes.length);
        when(multipartReader.read(any(), eq(52_428_800L))).thenReturn(bytes);
        when(sessionMapper.selectForUpdate(any())).thenReturn(session);
        when(sessionMapper.beginValidationIfInitialized(any())).thenReturn(1);
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy());
        when(contentPolicyService.validateBounded(any())).thenReturn(
                new ValidatedFileContent(bytes, bytes.length, sha, "application/pdf", ".pdf", "CLAMAV", "1"));
        when(storageReceiptApi.store(any())).thenReturn(
                new FileStorageReceipt("op-101", 501L, "evidence.pdf", "application/pdf", bytes.length));
        when(artifactMapper.insert(any())).thenReturn(1);
        when(versionMapper.insert(any())).thenAnswer(invocation -> {
            FileVersionDO row = invocation.getArgument(0);
            row.setId(301L);
            return 1;
        });
        when(referenceMapper.insert(any())).thenAnswer(invocation -> {
            FileReferenceDO row = invocation.getArgument(0);
            row.setId(401L);
            return 1;
        });
        when(artifactMapper.activateDraftIfMatch(any())).thenReturn(1);
        when(sessionMapper.completeIfValidating(any())).thenReturn(1);
        executeImmediately();

        FileUploadCompleted completed = service.complete(new FileUploadCompleteCommand(0L, 7L, "idem-complete",
                101L, 201L, new MockMultipartFile("file", "evidence.pdf", "application/pdf", bytes), null));

        assertEquals(101L, completed.artifactId());
        assertEquals(1, completed.versionNo());
        assertEquals(401L, completed.referenceId());
        assertEquals(sha, completed.sha256());
        assertEquals(2, successFacts.get().businessEvents().size());
        assertEquals(Set.of("FileVersionCommitted", "FileReferenceAttached"),
                successFacts.get().businessEvents().stream()
                        .map(PlatformCommandExecutionApi.BusinessEvent::eventType).collect(java.util.stream.Collectors.toSet()));
        ArgumentCaptor<FileArtifactDO> artifact = ArgumentCaptor.forClass(FileArtifactDO.class);
        verify(artifactMapper).insert(artifact.capture());
        assertEquals(101L, artifact.getValue().getId());
        assertEquals("DRAFT", artifact.getValue().getLifecycleStatusCode());
        assertNotNull(successFacts.get().detailSnapshot());
    }

    private void executeImmediately() {
        when(commandExecutionApi.execute(any(), anyString(), eq(FileUploadCompleted.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<FileUploadCompleted> operation = invocation.getArgument(3);
                    Function<FileUploadCompleted, PlatformCommandExecutionApi.SuccessFacts> factory =
                            invocation.getArgument(4);
                    FileUploadCompleted result = operation.get();
                    successFacts.set(factory.apply(result));
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });
    }

    private FileUploadSessionDO session(int size) {
        FileUploadSessionDO row = new FileUploadSessionDO();
        row.setId(201L);
        row.setModeCode("CREATE_ARTIFACT");
        row.setOwnerContext("SOL");
        row.setObjectType("CHANGE");
        row.setObjectId("900");
        row.setPurposeCode("EVIDENCE");
        row.setReferenceKey("slot-a");
        row.setFileName("evidence.pdf");
        row.setCategoryCode("EVIDENCE");
        row.setDeclaredSizeBytes((long) size);
        row.setDeclaredMediaType("application/pdf");
        row.setStorageOperationId("op-101");
        row.setStatusCode("INITIALIZED");
        row.setScopeVersion(8L);
        row.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        row.setVersion(0);
        row.setArtifactId(101L);
        row.setTenantId(0L);
        return row;
    }

    private FileBusinessObjectPolicyFact policy() {
        return new FileBusinessObjectPolicyFact(true, 8L, "MUTABLE", "SINGLE",
                Set.of("EVIDENCE"), Set.of("application/pdf"), 52_428_800L, "INTERNAL");
    }
}
