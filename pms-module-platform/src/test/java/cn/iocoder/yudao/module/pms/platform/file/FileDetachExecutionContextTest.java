package cn.iocoder.yudao.module.pms.platform.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.DetachFileReferenceCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileDetachExecutionContextTest {
    private final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    private final FileBusinessObjectPolicyRegistry policies = mock(FileBusinessObjectPolicyRegistry.class);
    private final SecurityFrameworkService permissions = mock(SecurityFrameworkService.class);
    private final FileReferenceMapper references = mock(FileReferenceMapper.class);
    private final FileLifecycleApplicationService service = new FileLifecycleApplicationService(commands,
            mock(OperationAuditApi.class), policies, permissions, mock(FileArtifactMapper.class),
            mock(FileVersionMapper.class), references, mock(FileUploadSessionMapper.class),
            mock(FileArchiveRecordMapper.class), new FileEventFactory());

    @Test
    void detachUsesTheSelectedNodeForAllPolicyChecks() {
        executeImmediately();
        var command = command();
        var policy = new FileBusinessObjectPolicyFact(true, 8L, "MUTABLE", "MULTIPLE",
                Set.of("DYNAMIC_FORM_ATTACHMENT"), Set.of("application/pdf"), 52428800L, "INTERNAL");
        when(policies.inspect(any())).thenReturn(policy);
        when(policies.lockAndRevalidate(any())).thenReturn(policy);
        var row = new FileReferenceDO();
        row.setId(301L); row.setArtifactId(101L); row.setFileVersionNo(1);
        row.setOwnerContext(command.ownerContext()); row.setObjectType(command.objectType());
        row.setObjectId(command.objectId()); row.setPurposeCode(command.purposeCode());
        row.setReferenceKey(command.referenceKey()); row.setStatusCode("ACTIVE"); row.setVersion(1);
        when(references.selectForUpdate(any())).thenReturn(row);
        when(references.updateStateIfMatch(any())).thenReturn(1);
        assertEquals("DETACHED", service.detach(command).status());
        verify(policies).inspect(argThat(query -> command.ownerExecutionContext().equals(query.ownerExecutionContext())));
        verify(policies).lockAndRevalidateReferenceSet(argThat(query -> command.ownerExecutionContext().equals(query.ownerExecutionContext())));
        verify(policies).lockAndRevalidate(argThat(query -> command.ownerExecutionContext().equals(query.ownerExecutionContext())));
        verify(references).updateStateIfMatch(any());
    }

    @Test
    void ownerRejectionStopsBeforeLockingOrRemovingTheFile() {
        executeImmediately();
        when(policies.inspect(any())).thenThrow(new IllegalStateException("STALE_EXECUTION"));
        assertThrows(IllegalStateException.class, () -> service.detach(command()));
        verifyNoInteractions(references);
        verify(policies, never()).lockAndRevalidateReferenceSet(any());
    }

    @Test
    void selectedExecutionDoesNotGrantFilePermission() {
        when(permissions.hasPermission("pms:file:manage")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.detach(command()));
        verifyNoInteractions(commands, policies, references);
    }

    private void executeImmediately() {
        when(permissions.hasPermission("pms:file:manage")).thenReturn(true);
        when(commands.execute(any(), anyString(), eq(FileLifecycleApplicationService.LifecycleResult.class), any(), any()))
                .thenAnswer(call -> {
                    Supplier<FileLifecycleApplicationService.LifecycleResult> operation = call.getArgument(3);
                    return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, operation.get());
                });
    }

    private DetachFileReferenceCommand command() {
        return new DetachFileReferenceCommand(0L, 7L, "detach-selected-node", 301L, 1, "PLATFORM",
                "DYNAMIC_FORM_INSTANCE", "31", "FORM_FIELD_ATTACHMENT/evidence", "slot-a", "材料重复",
                JsonUtils.parseTree("{\"task\":{\"executionId\":7001}}"));
    }
}
