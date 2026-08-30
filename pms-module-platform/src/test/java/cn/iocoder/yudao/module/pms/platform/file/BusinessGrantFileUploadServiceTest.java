package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.BusinessGrantFileUploadService;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessGrantFileUploadServiceTest {
    @Mock FileBusinessObjectPolicyRegistry policyRegistry;
    @Mock FileUploadApplicationService uploadService;
    @Mock FileUploadSessionMapper sessionMapper;
    @Mock FileArtifactMapper artifactMapper;
    @Mock FileVersionMapper versionMapper;
    @Mock FileReferenceMapper referenceMapper;
    @Mock OperationAuditApi operationAuditApi;

    @Test
    void initializeUsesIssuerAndServerSlotWithoutSecurityContext() {
        FileBusinessObjectPolicyFact filePolicy = new FileBusinessObjectPolicyFact(true, 3L,
                "IMMUTABLE", "SINGLE", Set.of("SATISFACTION_SIGNATURE"), Set.of("image/png"),
                1024L, "CONFIDENTIAL");
        when(policyRegistry.initializeBusinessGrantUploadPolicy(any())).thenAnswer(invocation -> {
            var query = (cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializePolicyQuery)
                    invocation.getArgument(0);
            return new BusinessGrantUploadPolicyFact(1L, 2, 11L, "req-1", 50L,
                    "SATISFACTION_SIGNATURE", query.fileSlotKey(), query.fileSequence(), 9L, 3L, filePolicy);
        });
        when(uploadService.initializeAuthorized(any(), any())).thenReturn(
                new FileUploadInitialized(100L, 101L, LocalDateTime.now().plusMinutes(10)));
        when(sessionMapper.selectBusinessGrantSlotsForUpdate(any())).thenReturn(java.util.List.of());
        var service = new BusinessGrantFileUploadService(policyRegistry, uploadService, sessionMapper,
                artifactMapper, versionMapper, referenceMapper, operationAuditApi);

        var initialized = service.initialize(new BusinessGrantUploadInitializeCommand(7L, 1L, 2, 11L,
                "req-1", 50L, "SATISFACTION_SIGNATURE", "file-op-1", "sign.png",
                "SATISFACTION_SIGNATURE", 10L, "image/png", null));

        assertEquals(50L, initialized.responseId());
        assertEquals(1, initialized.fileSequence());
        assertEquals("grant-file:50:1:file-op-1", initialized.fileSlotKey());
        ArgumentCaptor<FileUploadInitializeCommand> command =
                ArgumentCaptor.forClass(FileUploadInitializeCommand.class);
        verify(uploadService).initializeAuthorized(command.capture(), any());
        assertEquals(9L, command.getValue().actorUserId());
        assertEquals("50", command.getValue().objectId());
        assertEquals(initialized.fileSlotKey(), command.getValue().referenceKey());
    }
}
