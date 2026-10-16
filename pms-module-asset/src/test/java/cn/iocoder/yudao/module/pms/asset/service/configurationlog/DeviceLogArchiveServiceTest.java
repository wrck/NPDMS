package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogArchiveApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsLogSplitApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceLogArchiveServiceTest {
    final DeviceLogImportMapper imports = mock(DeviceLogImportMapper.class);
    final DeviceLogFileMapper files = mock(DeviceLogFileMapper.class);
    final DeviceLogVersionMapper versions = mock(DeviceLogVersionMapper.class);
    final DeviceLogDeviceMapper deviceQueries = mock(DeviceLogDeviceMapper.class);
    final DeviceMapper devices = mock(DeviceMapper.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final DeviceOpsLogSplitApi dac = mock(DeviceOpsLogSplitApi.class);
    final DeviceLogFileApi storage = mock(DeviceLogFileApi.class);
    final FileEvidenceApi evidence = mock(FileEvidenceApi.class);
    final cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi rules = mock(cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi.class);
    DeviceLogArchiveService service;
    final List<DeviceLogFileDO> insertedFiles = new ArrayList<>();
    final String operation = UUID.randomUUID().toString();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
        service = new DeviceLogArchiveService(imports, files, versions, deviceQueries, devices, permissions,
                provider(scopes), provider(dac), provider(storage), provider(evidence), provider(mock(NativeGeneratedFileApi.class)), provider(null), provider(rules));
        when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        var scope = new ProjectScopeResult(10L, 1L, Set.of(10L), Set.of());
        when(scopes.lockAndRevalidate(any())).thenReturn(scope); when(scopes.resolveCurrent(any())).thenReturn(scope);
        when(imports.insert(any(DeviceLogImportDO.class))).thenAnswer(inv -> { inv.getArgument(0,DeviceLogImportDO.class).setId(100L); return 1; });
        when(files.importFiles(any())).thenReturn(List.of());
        when(files.lockImportFiles(any())).thenReturn(List.of());
        when(files.insert(any(DeviceLogFileDO.class))).thenAnswer(inv -> {
            var row = inv.getArgument(0,DeviceLogFileDO.class); row.setId(200L+insertedFiles.size()); insertedFiles.add(row); return 1;
        });
        when(files.updateById(any(DeviceLogFileDO.class))).thenReturn(1);
        when(versions.allocate(any())).thenReturn(1); when(versions.current(any())).thenReturn(1L);
        when(storage.store(any())).thenReturn(new NativeGeneratedFileApi.RegisteredFile(300L,400L,500L,1,"safe-sha","log.txt"));
        when(dac.split(any())).thenReturn(split("SN001"));
        when(deviceQueries.matchSerials(any())).thenReturn(List.of(device(20L,"SN001")));
        when(devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(1L,20L))).thenReturn(device(20L,"SN001"));
    }
    @AfterEach void teardown() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void scopeVersionIsRequiredAndChangesRejectBeforeSplitting() {
        when(scopes.lockAndRevalidate(any())).thenAnswer(inv -> {
            var q=inv.getArgument(0,cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery.class);
            assertEquals(1L,q.expectedScopeVersion());
            return new ProjectScopeResult(10L,2L,Set.of(10L),Set.of());
        });
        assertThrows(RuntimeException.class,() -> upload("input"));
        verifyNoInteractions(dac,storage);assertTrue(insertedFiles.isEmpty());
    }
    @Test void unassociatedSerialRejectsWholeBatchBeforeWritingAnyFile() {
        when(dac.split(any())).thenReturn(split("SN001","SN002"));
        var result = upload("input");
        assertEquals("REJECTED", result.outcome()); assertEquals(0, result.deviceCount());
        verifyNoInteractions(storage); verify(devices,never()).selectByTenantAndIdForUpdate(anyLong(),anyLong());
        verify(files,never()).insert(any(DeviceLogFileDO.class));
    }
    @Test void callbackSerialMustMatchTheTaskDevice() {
        when(storage.readCollection(any())).thenReturn("callback".getBytes(StandardCharsets.UTF_8));
        when(deviceQueries.matchSerials(any())).thenReturn(List.of(device(21L,"SN001")));
        service.receiveCollection(new DeviceConfigurationLogArchiveApi.CollectionLog(1L,7L,10L,20L,"task",1L,9L));
        verify(storage,never()).store(any());
        var captor = org.mockito.ArgumentCaptor.forClass(DeviceLogImportDO.class);
        verify(imports).insert(captor.capture()); assertEquals("REJECTED",captor.getValue().getOutcome());
    }
    @Test void callbackUsesNativeConsumePermissionWithoutGrantingManualImport() {
        when(permissions.hasAnyPermissions(anyLong(),any(String[].class))).thenAnswer(inv ->
                Arrays.asList((String[]) inv.getRawArguments()[1]).contains("pms:imp-configuration:update"));
        when(storage.readCollection(any())).thenReturn("callback".getBytes(StandardCharsets.UTF_8));
        service.receiveCollection(new DeviceConfigurationLogArchiveApi.CollectionLog(1L,7L,10L,20L,"task",1L,9L));
        assertEquals("COLLECTION_CALLBACK",insertedFiles.get(1).getSourceType());
        assertThrows(RuntimeException.class,()->upload("input"));
    }
    @Test void manualAndCollectionShareMonotonicDeviceVersionWithoutUpdatingHistory() {
        when(versions.current(new DeviceLogVersionQuery(1L,20L))).thenReturn(5L);
        assertEquals("ARCHIVED",upload("input").outcome());
        assertEquals(2,insertedFiles.size());
        assertNull(insertedFiles.getFirst().getDeviceId()); // sanitized complete source is retained
        assertEquals(5L,insertedFiles.get(1).getDeviceVersion());
        assertEquals("MANUAL_UPLOAD",insertedFiles.get(1).getSourceType());
        verify(files,never()).deleteById(anyLong());
    }
    @Test void replayReturnsOriginalImportAndChangedPayloadIsRejected() {
        var row = new DeviceLogImportDO(); row.setId(100L); row.setTenantId(1L); row.setProjectId(10L); row.setActorId(7L);
        row.setSourceType("MANUAL_UPLOAD"); row.setSourceFileName("big.log"); row.setOutcome("ARCHIVED");
        row.setInputSha256(org.apache.commons.codec.digest.DigestUtils.sha256Hex("input"));
        when(imports.findImport(any())).thenReturn(row);
        assertEquals(100L,upload("input").id()); assertThrows(RuntimeException.class, () -> upload("changed"));
        verifyNoInteractions(dac,storage); verify(imports,never()).insert(any(DeviceLogImportDO.class));
    }
    @Test void concurrentSameSourceReturnsCommittedResultWithoutStoringAnotherFile() {
        var row = new DeviceLogImportDO(); row.setId(101L); row.setTenantId(1L); row.setProjectId(10L); row.setActorId(7L);
        row.setSourceType("MANUAL_UPLOAD"); row.setSourceFileName("big.log"); row.setOutcome("ARCHIVED");
        row.setInputSha256(org.apache.commons.codec.digest.DigestUtils.sha256Hex("input"));
        when(imports.insert(any(DeviceLogImportDO.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("source key exists"));
        when(imports.lockImport(any())).thenReturn(row);
        assertEquals(101L,upload("input").id());
        verifyNoInteractions(storage); verify(files,never()).insert(any(DeviceLogFileDO.class));
        row.setInputSha256("different");
        assertThrows(RuntimeException.class, () -> upload("input"));
        verifyNoInteractions(storage);
    }
    @Test void deniedScopeAndForeignTenantNeverReachDacOrStorage() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> upload("input"));
        assertThrows(RuntimeException.class, () -> service.receiveCollection(new DeviceConfigurationLogArchiveApi.CollectionLog(2L,7L,10L,20L,"task",1L,9L)));
        verifyNoInteractions(dac,storage);
    }
    @Test void manualImportRequiresNativeFileUploadPermissionBeforeSendingContentToDac() {
        when(permissions.hasAnyPermissions(7L,"pms:file:upload")).thenReturn(false);
        assertThrows(RuntimeException.class,()->upload("input"));
        verifyNoInteractions(dac,storage);verify(imports,never()).insert(any(DeviceLogImportDO.class));
    }
    @Test void invalidNewerFileFallsBackToPreviousAvailableFileForSameDevice() {
        var newer = file(2L,6L); var older = file(1L,5L);
        when(files.projectFiles(any())).thenReturn(List.of(newer,older));
        when(deviceQueries.projectDevices(any())).thenReturn(List.of(device(20L,"SN001"),device(21L,"SN002")));
        when(evidence.inspectDocument(1L,2L)).thenReturn(document(2L,false));
        when(evidence.inspectDocument(1L,1L)).thenReturn(document(1L,true));
        var latest = service.latest(10L);
        assertEquals(1, latest.size()); assertEquals(5L,latest.getFirst().latest().deviceVersion());
    }
    @Test void latestExcludesDevicesWithoutUploadsButRetainsUnavailableArchiveHistory() {
        var archived = file(2L, 6L);
        when(files.projectFiles(any())).thenReturn(List.of(archived));
        when(deviceQueries.projectDevices(any())).thenReturn(List.of(device(20L, "SN001"), device(21L, "NO-LOG")));
        when(evidence.inspectDocument(1L, 2L)).thenReturn(document(2L, false));
        var result = service.latest(10L);
        assertEquals(1, result.size());
        assertEquals("SN001", result.getFirst().serialNumber());
        assertEquals(6L, result.getFirst().latest().deviceVersion());
        assertFalse(result.getFirst().latest().available());
        when(files.projectFiles(any())).thenReturn(List.of());
        assertTrue(service.latest(10L).isEmpty());
    }
    @Test void taskOriginIsPersistedBeforeFileEventsAndResolvedOnlyForDeviceFiles() {
        when(rules.documentTargets(any())).thenReturn(Set.of("D"));
        when(storage.store(any())).thenAnswer(inv -> {
            assertEquals(13L, insertedFiles.getLast().getOriginTaskId());
            return new NativeGeneratedFileApi.RegisteredFile(300L,400L,500L,1,"safe-sha","log.txt");
        });
        assertEquals("ARCHIVED", service.upload(new DeviceLogArchiveService.Upload(
                10L, operation, "task.log", "content".getBytes(StandardCharsets.UTF_8), 13L)).outcome());
        var batch = org.mockito.ArgumentCaptor.forClass(DeviceLogImportDO.class);
        verify(imports).insert(batch.capture()); assertEquals(13L, batch.getValue().getOriginTaskId());
        var sources = new DeviceLogDocumentSources(files);
        for (var row : insertedFiles) {
            when(files.selectById(row.getId())).thenReturn(row);
            var doc = new FileEvidenceApi.Document(400L,"AST",DeviceLogFilePolicyProvider.OBJECT,row.getId().toString(),
                    DeviceLogFilePolicyProvider.PURPOSE,"device-log",500L,1,"safe-sha","log.txt",true);
            var scope = sources.resolve(1L,doc);
            if (row.getDeviceId() == null) assertNull(scope.originTaskId());
            else assertEquals(13L, scope.originTaskId());
        }
    }

    @Test void invalidTaskOriginIsRejectedBeforeDacAndFileWrites() {
        when(rules.documentTargets(any())).thenReturn(Set.of());
        assertThrows(RuntimeException.class, () -> service.upload(new DeviceLogArchiveService.Upload(
                10L, operation, "task.log", "content".getBytes(StandardCharsets.UTF_8), 999L)));
        verifyNoInteractions(dac, storage);
        verify(imports, never()).insert(any(DeviceLogImportDO.class));
    }
    @Test void sameOperationCannotChangeTaskOrigin() {
        var existing = new DeviceLogImportDO(); existing.setOriginTaskId(12L);
        when(imports.findImport(any())).thenReturn(existing);
        when(rules.documentTargets(any())).thenReturn(Set.of("D"));
        assertThrows(RuntimeException.class, () -> service.upload(new DeviceLogArchiveService.Upload(
                10L, operation, "task.log", "content".getBytes(StandardCharsets.UTF_8), 13L)));
        verifyNoInteractions(dac, storage);
    }

    private DeviceLogArchiveService.ImportView upload(String content) { return service.upload(new DeviceLogArchiveService.Upload(10L,operation,"big.log",content.getBytes(StandardCharsets.UTF_8))); }
    private DeviceOpsLogSplitApi.Result split(String... serials) { return new DeviceOpsLogSplitApi.Result("safe source",0,
            Arrays.stream(serials).map(sn -> new DeviceOpsLogSplitApi.DeviceLog(sn,"SN: "+sn+"\nfull output\n")).toList()); }
    private DeviceDO device(long id,String sn) { var d=new DeviceDO();d.setId(id);d.setTenantId(1L);d.setProjectId(10L);d.setSn(sn);return d; }
    private DeviceLogFileDO file(long id,long version) { var f=new DeviceLogFileDO();f.setId(id);f.setTenantId(1L);f.setProjectId(10L);f.setDeviceId(20L);
        f.setDeviceVersion(version);f.setFileReferenceId(id);f.setSha256("safe-sha");return f; }
    private FileEvidenceApi.Document document(long id,boolean available) { return new FileEvidenceApi.Document(id,"AST","DeviceConfigurationLogFile",
            "1","DEVICE_CONFIGURATION_LOG","device-log",10L,1,"safe-sha","log.txt",available); }
    @SuppressWarnings("unchecked") private <T> ObjectProvider<T> provider(T value) {
        var provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value);when(provider.getIfAvailable()).thenReturn(value);return provider;
    }
}
