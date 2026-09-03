package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFileCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFilePolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileContentPolicyService;
import cn.iocoder.yudao.module.pms.platform.service.file.GeneratedBusinessFileService;
import cn.iocoder.yudao.module.pms.platform.service.file.GeneratedBusinessFileTransactionService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.GeneratedBusinessFileReservation;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
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
    private GeneratedBusinessFileService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        service = new GeneratedBusinessFileService(transactions, policyRegistry, contentPolicyService,
                sessionMapper, artifactMapper, versionMapper, referenceMapper, permissionApi);
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
        assertEquals(4, policyQuery.getValue().expectedTaskVersion());

        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(transactions).completeSession(eq(7L), eq(50L), any(), eq(receipt));
    }

    @Test
    void rejectsMissingUploadPermissionBeforeOwnerOrStorage() {
        when(permissionApi.hasAnyPermissions(30L, "pms:file:upload")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.create(command()));
        verifyNoInteractions(policyRegistry, contentPolicyService, transactions);
    }

    private GeneratedBusinessFileCommand command() {
        return new GeneratedBusinessFileCommand(7L, 30L, "result-op-1", 40L, 10L, 11L, 12L, 4,
                "ACC", "SATISFACTION_RESULT", "SATISFACTION_RESULT_DOCUMENT", 9L,
                "result.pdf", "application/pdf", new byte[]{1, 2, 3});
    }
}
