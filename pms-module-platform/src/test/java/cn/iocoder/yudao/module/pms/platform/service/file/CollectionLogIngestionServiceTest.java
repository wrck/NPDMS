package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionLogIngestionApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionCallbackApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionLogQuarantineMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionLogIngestionServiceTest {
    @Test void serializedCallbackLogPassesRealContentPolicyWithoutRelaxingBinaryRejection() throws Exception {
        var policy = new FileContentPolicyService(new BoundedMultipartReader(), java.util.List.of(), false);
        var fact = new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact(true, 0L,
                "IMMUTABLE", "SINGLE", java.util.Set.of("COLLECTION_LOG"), java.util.Set.of("text/plain"),
                20L * 1024 * 1024, "INTERNAL");
        byte[] bytes = "{\"status\":\"SUCCEEDED\",\"output\":\"device output\\n完成\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var result = policy.validateBounded(new cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand(
                bytes, "collection-1-1.txt", bytes.length, "text/plain", sha, fact));
        assertArrayEquals(bytes, result.content());
        assertEquals(sha, result.sha256());
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> policy.validateBounded(
                new cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand(
                        new byte[]{0, 1, 2, 3}, "collection-1-1.txt", 4, "text/plain", null, fact)));
    }

    @Test void rejectedScanPersistsQuarantineEvidenceWithoutCreatingAFileVersion() {
        var tasks = mock(CollectionTaskMapper.class);
        var artifacts = mock(FileArtifactMapper.class);
        var versions = mock(FileVersionMapper.class);
        var references = mock(FileReferenceMapper.class);
        var policy = mock(FileContentPolicyService.class);
        var storage = mock(CollectionLogStorageService.class);
        var callbacks = mock(CollectionCallbackApi.class);
        var quarantines = mock(CollectionLogQuarantineMapper.class);
        var task = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO();
        task.setId(12L); task.setExternalTaskId("dac"); task.setStatus("DISPATCHED");
        when(tasks.selectByTenantAndPlatformTaskIdForUpdate(7L, "task")).thenReturn(task);
        var rejected = cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SECURITY_SCAN_REJECTED;
        when(policy.validateBounded(org.mockito.ArgumentMatchers.any())).thenThrow(
                new cn.iocoder.yudao.framework.common.exception.ServiceException(rejected.getCode(), rejected.getMsg()));
        when(quarantines.insert(org.mockito.ArgumentMatchers.any(
                cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionLogQuarantineDO.class)))
                .thenAnswer(call -> { ((cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionLogQuarantineDO)
                        call.getArgument(0)).setId(99L); return 1; });
        var service = new CollectionLogIngestionService(tasks, artifacts, versions, references, policy, storage, callbacks, quarantines);
        TenantContextHolder.setTenantId(7L);
        try {
            service.ingest(command(7L, "a".repeat(64)), new ByteArrayInputStream(new byte[3]));
            var callback = org.mockito.ArgumentCaptor.forClass(
                    cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackCommand.class);
            verify(callbacks).handleCallback(callback.capture());
            assertEquals("99", callback.getValue().quarantineEvidenceId());
            assertNull(callback.getValue().fileVersionId());
            verifyNoInteractions(artifacts, versions, references);
        } finally { TenantContextHolder.clear(); }
    }

    @Test void storageIdentityFitsInfraContractAndChangesWithTenantOrContent() {
        String first = CollectionLogIngestionService.storageOperation(command(7L, "a".repeat(64)));
        assertTrue(first.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"));
        assertEquals(first, CollectionLogIngestionService.storageOperation(command(7L, "a".repeat(64))));
        assertNotEquals(first, CollectionLogIngestionService.storageOperation(command(8L, "a".repeat(64))));
        assertNotEquals(first, CollectionLogIngestionService.storageOperation(command(7L, "b".repeat(64))));
    }

    @Test void missingTaskBindingCannotCreateAFileOrAdvanceCollection() {
        var tasks = mock(CollectionTaskMapper.class);
        var artifacts = mock(FileArtifactMapper.class);
        var versions = mock(FileVersionMapper.class);
        var references = mock(FileReferenceMapper.class);
        var policy = mock(FileContentPolicyService.class);
        var storage = mock(CollectionLogStorageService.class);
        var callbacks = mock(CollectionCallbackApi.class);
        var service = new CollectionLogIngestionService(tasks, artifacts, versions, references, policy, storage, callbacks,
                mock(CollectionLogQuarantineMapper.class));
        TenantContextHolder.setTenantId(7L);
        try {
            assertThrows(IllegalStateException.class, () -> service.ingest(command(7L, "a".repeat(64)), new ByteArrayInputStream(new byte[3])));
            verifyNoInteractions(artifacts, versions, references, policy, storage, callbacks);
        } finally { TenantContextHolder.clear(); }
    }

    private CollectionLogIngestionApi.Command command(long tenant, String hash) {
        return new CollectionLogIngestionApi.Command(tenant, 1L, "callback", "task", "dac", "SUCCEEDED", 1, 1, 3, hash, null, null);
    }
}
