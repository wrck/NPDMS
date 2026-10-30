package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogParsedFactApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.*;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceArchivedLogParsingServiceTest {
    final DeviceLogParseJobMapper jobs=mock(DeviceLogParseJobMapper.class);
    final DeviceLogFileMapper files=mock(DeviceLogFileMapper.class);
    final DeviceLogImportMapper imports=mock(DeviceLogImportMapper.class);
    final DeviceLogParsingApi parser=mock(DeviceLogParsingApi.class);
    final DeviceLogParsedFactApi projection=mock(DeviceLogParsedFactApi.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    final FileEvidenceApi evidence=mock(FileEvidenceApi.class);
    final DeviceLogParseSnapshotMapper snapshots=mock(DeviceLogParseSnapshotMapper.class);
    DeviceLogParseSnapshotDO stored;
    DeviceArchivedLogParsingService service;
    DeviceLogFileDO file;
    DeviceLogParseJobDO job;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(evidence.lockAndRevalidate(any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Fact(true,"valid",1,1,1));
        var user=new LoginUser();user.setId(7L);user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
        ObjectProvider<DeviceLogParsingApi> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(parser);
        service=new DeviceArchivedLogParsingService(jobs,files,imports,provider,projection,scopes,tx,evidence,mock(DeviceLogSlotProjectionService.class),snapshots);
        when(snapshots.selectById(90L)).thenAnswer(i -> stored);
        when(snapshots.selectByFileForUpdate(any())).thenAnswer(i -> stored);
        when(snapshots.insert(any(DeviceLogParseSnapshotDO.class))).thenAnswer(i -> {stored=i.getArgument(0);return 1;});
        file=new DeviceLogFileDO();file.setId(90L);file.setTenantId(1L);file.setProjectId(10L);file.setDeviceId(91L);
        file.setSerialNumber("SN-A");file.setImportId(80L);file.setFileReferenceId(11L);file.setArtifactId(22L);file.setArtifactVersion(1);file.setSha256("known");
        job=new DeviceLogParseJobDO();job.setId(90L);job.setTenantId(1L);job.setProjectId(10L);job.setActorId(7L);
        job.setState("SUCCEEDED");job.setExternalTaskId("native");job.setLogReceivedAt(LocalDateTime.of(2026,10,7,10,0));
        when(files.selectById(90L)).thenReturn(file);when(jobs.selectById(90L)).thenAnswer(i -> job);
        when(jobs.lock(any())).thenAnswer(i -> job);when(jobs.updateById(any(DeviceLogParseJobDO.class))).thenReturn(1);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(evidence.inspectDocument(1L,11L)).thenReturn(document(true,"90"));
        when(parser.find(anyString(),eq("10"),isNull())).thenReturn(result("SN-A"));
        when(projection.record(any())).thenReturn(new DeviceLogParsedFactApi.Outcome("LINKED",91L,"SN-A","UPDATED","updated"));
    }
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TenantContextHolder.clear();SecurityContextHolder.clearContext();
    }
    @Test void viewingStructuredResultDoesNotProjectOrResubmitHistoricalLog() {
        var view=service.getResult(90L);
        assertEquals("SUCCEEDED",view.state());assertNotNull(view.result());
        assertEquals(result("SN-A").result(),view.result());
        verifyNoInteractions(projection);
        verify(parser,never()).submit(any());verify(jobs,never()).updateById(any(DeviceLogParseJobDO.class));
    }
    @Test void resultViewingCannotBypassTenantScopeEvidenceOrTaskBinding() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,() -> service.getResult(90L));
        TenantContextHolder.setTenantId(1L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,() -> service.getResult(90L));
        verifyNoInteractions(parser);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(evidence.inspectDocument(1L,11L)).thenReturn(document(false,"90"));
        assertNull(service.getResult(90L).result());
        when(evidence.inspectDocument(1L,11L)).thenReturn(document(true,"other"));
        assertNull(service.getResult(90L).result());
        when(evidence.inspectDocument(1L,11L)).thenReturn(document(true,"90"));
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false,"quarantined",1,2,1));
        assertNull(service.getResult(90L).result());
        job.setExternalTaskId("changed");
        assertThrows(IllegalArgumentException.class,() -> service.getResult(90L));
        verifyNoInteractions(projection);
    }
    @Test void mismatchedDeviceOrPendingParseDoesNotExposeStructuredResult() {
        when(parser.find(anyString(),eq("10"),isNull())).thenReturn(result("SN-B"));
        assertNull(service.getResult(90L).result());
        when(parser.find(anyString(),eq("10"),isNull())).thenReturn(new DeviceLogParsingApi.ParseTask("native","RUNNING",null,"1.3.0",List.of(),null));
        assertEquals("RUNNING",service.getResult(90L).state());assertNull(service.getResult(90L).result());
        verifyNoInteractions(projection);
    }
    @Test void savedResultAndCompletedStateWorkWithoutParserAndNeverRewriteHistory() {
        var original=service.getResult(90L);
        var json=stored.getResultJson();
        reset(parser);when(parser.find(anyString(),anyString(),any())).thenThrow(new DeviceLogParsingApi.ParsingException("offline"));
        job.setProjected(true);
        assertEquals(original.result(),service.getResult(90L).result());
        assertEquals("V2",service.get(90L).conpVersion());
        assertEquals(json,stored.getResultJson());
        verify(snapshots,times(1)).insert(any(DeviceLogParseSnapshotDO.class));
        verifyNoInteractions(parser,projection);
    }
    @Test void cachedResultStillRejectsScopeQuarantineAndChangedSource() {
        service.getResult(90L);reset(parser);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,() -> service.getResult(90L));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false,"quarantined",1,2,1));
        assertNull(service.getResult(90L).result());
        file.setSha256("changed");assertThrows(IllegalArgumentException.class,() -> service.getResult(90L));
        verifyNoInteractions(parser,projection);
    }
    @Test void unavailableParserWithoutSnapshotDoesNotReuploadOrCreateSnapshot() {
        when(parser.find(anyString(),anyString(),any())).thenThrow(new DeviceLogParsingApi.ParsingException("offline"));
        assertNull(service.getResult(90L).result());
        assertNull(stored);verify(parser,never()).submit(any());verifyNoInteractions(projection);
    }
    @Test void invalidEnvelopeCannotBeStoredAsLocalEvidence() {
        when(parser.find(anyString(),anyString(),any())).thenReturn(new DeviceLogParsingApi.ParseTask("native","SUCCEEDED",null,"1.3.0",List.of(),Map.of("taskId","another","semanticResult",Map.of())));
        assertThrows(IllegalArgumentException.class,() -> service.getResult(90L));assertNull(stored);
    }
    @Test void concurrentFirstReaderUsesLockedSnapshotAndCannotReplaceItsContent() {
        service.getResult(90L);
        var originalJson=stored.getResultJson();
        // Simulate an earlier repeatable-read view that missed another reader's committed insert.
        when(snapshots.selectById(90L)).thenReturn(null);
        assertNotNull(service.getResult(90L).result());
        when(parser.find(anyString(),anyString(),any())).thenReturn(new DeviceLogParsingApi.ParseTask(
                "native","SUCCEEDED",null,"1.3.0",List.of(),Map.of("taskId","native","semanticResult",Map.of())));
        assertThrows(IllegalArgumentException.class,() -> service.getResult(90L));
        assertEquals(originalJson,stored.getResultJson());
        verify(snapshots,times(1)).insert(any(DeviceLogParseSnapshotDO.class));
    }
    @Test void archivedParsePassesAllFourObservedVersionsToProjection() {
        var snapshot=List.of(
                Map.of("semanticKey","device.identity.serialNumber","status","OBSERVED","value","SN-A"),
                Map.of("semanticKey","device.software.version","status","OBSERVED","value","SOFT"),
                Map.of("semanticKey","device.hardware.cpldVersion","status","OBSERVED","value","CPLD"),
                Map.of("semanticKey","device.hardware.pcbVersion","status","OBSERVED","value","PCB"),
                Map.of("semanticKey","device.hardware.bootloader.basicVersion","status","OBSERVED","value","BASIC"));
        when(parser.find(anyString(),eq("10"),isNull())).thenReturn(new DeviceLogParsingApi.ParseTask("native","SUCCEEDED",null,"1.3.1",List.of(),Map.of("taskId","native","semanticResult",Map.of("snapshot",snapshot))));
        service.get(90L);
        verify(projection).record(argThat(c -> "SOFT".equals(c.conpVersion()) && "CPLD".equals(c.cpldVersion())
                && "PCB".equals(c.pcbVersion()) && "BASIC".equals(c.bootVersion())));
    }
    @Test void successfulParseUsesFrozenSerialAndLogReceiveTime() {
        var view=service.get(90L);assertEquals("V2",view.conpVersion());assertEquals("UPDATED",view.deviceProjection().versionStatus());
        var command=org.mockito.ArgumentCaptor.forClass(DeviceLogParsedFactApi.ParsedLog.class);verify(projection).record(command.capture());
        assertEquals("SN-A",command.getValue().serialNumber());assertEquals(job.getLogReceivedAt(),command.getValue().logReceivedAt());
        assertEquals("ast-file:90",command.getValue().logKey());assertEquals(90L,command.getValue().sourceFileId());
        verify(parser).find(matches("[a-f0-9-]{36}"),eq("10"),isNull());verify(parser,never()).submit(any());
    }
    @Test void quarantineAfterInspectionStillPreventsVersionProjection() {
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false,"unavailable",1,2,1));
        assertTrue(service.get(90L).message().contains("不可用"));
        verifyNoInteractions(projection);
    }
    @Test void mismatchedSerialCannotUpdateEitherDevice() {
        when(parser.find(anyString(),eq("10"),isNull())).thenReturn(result("SN-B"));
        assertTrue(service.get(90L).message().contains("不一致"));verifyNoInteractions(projection);
    }
    @Test void quarantinedOrWrongOwnerEvidenceCannotBecomeCurrentVersion() {
        when(evidence.inspectDocument(1L,11L)).thenReturn(document(false,"90"));
        assertTrue(service.get(90L).message().contains("不可用"));
        when(evidence.inspectDocument(1L,11L)).thenReturn(document(true,"another-file"));
        assertTrue(service.get(90L).message().contains("不可用"));verifyNoInteractions(projection);
    }
    @Test void namespaceAndProjectScopeRejectBeforeQueryingParser() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,() -> service.get(90L));
        TenantContextHolder.setTenantId(1L);when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,() -> service.get(90L));verifyNoInteractions(parser,projection);
    }
    @Test void nativeTaskIdentityCannotBeReplaced() {
        job.setExternalTaskId("other-task");
        assertThrows(IllegalArgumentException.class,() -> service.get(90L));verifyNoInteractions(projection);
    }
    @Test void archiveRollbackCannotSubmitParserBeforeCommit() {
        TransactionSynchronizationManager.initSynchronization();
        var batch=new DeviceLogImportDO();batch.setId(80L);batch.setTenantId(1L);batch.setActorId(7L);
        when(imports.selectById(80L)).thenReturn(batch);when(jobs.insert(any(DeviceLogParseJobDO.class))).thenReturn(1);
        service.register(file,"sanitized content");
        assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());verifyNoInteractions(parser,projection);
        // Clearing the callbacks simulates the rollback path; no afterCommit submission can happen.
        TransactionSynchronizationManager.clearSynchronization();verifyNoInteractions(parser);
    }
    private FileEvidenceApi.Document document(boolean available,String id) {
        return new FileEvidenceApi.Document(11L,"AST","DeviceConfigurationLogFile",id,"DEVICE_CONFIGURATION_LOG","device-log",22L,1,"known","file.log",available);
    }
    private DeviceLogParsingApi.ParseTask result(String serial) {
        return new DeviceLogParsingApi.ParseTask("native","SUCCEEDED",null,"1.3.0",List.of(),Map.of("taskId","native","semanticResult",Map.of("snapshot",Map.of("entities",Map.of("device",Map.of(
                "serial",Map.of("semanticKey","device.identity.serialNumber","status","OBSERVED","value",serial),
                "version",Map.of("semanticKey","device.software.version","status","OBSERVED","value","V2")))))));
    }
}
