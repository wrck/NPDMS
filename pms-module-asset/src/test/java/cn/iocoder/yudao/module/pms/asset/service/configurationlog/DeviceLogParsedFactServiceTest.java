package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogParsedFactApi.ParsedLog;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParsedFactDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceLogParsedFactServiceTest {
    final DeviceMapper devices=mock(DeviceMapper.class);
    final DeviceLogParsedFactMapper facts=mock(DeviceLogParsedFactMapper.class);
    final DeviceNetworkVersionMapper versions=mock(DeviceNetworkVersionMapper.class);
    final DeviceLogVersionProjectionMapper projections=mock(DeviceLogVersionProjectionMapper.class);
    final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    final cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper assemblies=mock(cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper.class);
    final DeviceLogParsedFactService service=new DeviceLogParsedFactService(devices,facts,versions,projections,scopes,assemblies);
    final Map<String,DeviceLogParsedFactDO> rows=new LinkedHashMap<>();
    DeviceDO device;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(10L),Set.of()));
        device=new DeviceDO();device.setId(91L);device.setTenantId(1L);device.setProjectId(10L);device.setSn("SN-A");
        when(devices.selectByTenantAndSn(1L,"SN-A")).thenReturn(device);
        when(devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(1L,91L))).thenReturn(device);
        when(facts.byRequest(eq(1L),anyString())).thenAnswer(i -> rows.get(i.getArgument(1)));
        when(facts.history(any())).thenAnswer(i -> new ArrayList<>(rows.values()));
        when(facts.insert(any(DeviceLogParsedFactDO.class))).thenAnswer(i -> {
            DeviceLogParsedFactDO row=i.getArgument(0);row.setId((long)rows.size()+1);rows.put(row.getRequestKey(),row);return 1;
        });
        when(versions.insert(any(DeviceNetworkVersionDO.class))).thenReturn(1);
        when(projections.replaceCurrent(any())).thenReturn(1);
        when(projections.updateDeviceVersion(any())).thenReturn(1);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void unassignedSlotChildRequiresEffectiveAuthorizedParent() {
        var parent=new DeviceDO();parent.setId(92L);parent.setSn("PARENT");parent.setProjectId(10L);
        when(devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(1L,92L))).thenReturn(parent);
        var edge=new cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceAssemblyDO();
        edge.setParentDeviceSn("PARENT");edge.setChildDeviceSn("SN-A");edge.setPositionCode("SLOT_4");
        when(assemblies.selectCurrentTree(any())).thenReturn(List.of(edge));device.setProjectId(null);
        assertEquals("UPDATED",service.recordSlot(command("slot-child",7,"SOFT-C"),92L,"SLOT_4").versionStatus());
        assertNull(device.getProjectId());
    }
    @Test void missingSlotProofCannotEnableUnassignedDeviceProjection() {
        var parent=new DeviceDO();parent.setId(92L);parent.setSn("PARENT");parent.setProjectId(10L);
        when(devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(1L,92L))).thenReturn(parent);
        when(assemblies.selectCurrentTree(any())).thenReturn(List.of());device.setProjectId(null);
        assertThrows(IllegalArgumentException.class,()->service.recordSlot(command("no-slot",7,"SOFT-C"),92L,"SLOT_4"));
        verify(facts,never()).insert(any(DeviceLogParsedFactDO.class));verifyNoInteractions(versions,projections);
    }
    @Test void ordinaryLogStillCannotProjectUnassignedDevice() {
        device.setProjectId(null);
        assertEquals("DEVICE_NOT_IN_PROJECT",service.record(command("not-slot",7,"SOFT-C")).status());verifyNoInteractions(versions,projections);
    }
    @Test void allFourObservedVersionsReplaceCurrentTogether() {
        var c=command("four",7,"SOFT-2");
        service.record(new ParsedLog(c.tenantId(),c.actorId(),c.projectId(),c.requestKey(),c.logKey(),c.logReceivedAt(),c.parserTaskId(),c.parserVersion(),c.sourceFileId(),c.serialNumber(),c.conpVersion(),"BASIC-2","CPLD-2","PCB-2"));
        var captured=org.mockito.ArgumentCaptor.forClass(DeviceNetworkVersionDO.class);
        verify(versions).insert(captured.capture());
        assertEquals("SOFT-2",captured.getValue().getConpVersion());
        assertEquals("BASIC-2",captured.getValue().getBootVersion());
        assertEquals("CPLD-2",captured.getValue().getCpldVersion());
        assertEquals("PCB-2",captured.getValue().getPcbVersion());
    }
    @Test void missingHardwareFactsPreserveCurrentWithoutInventingHistory() {
        var old=new DeviceNetworkVersionDO();old.setId(71L);old.setBootVersion("BASIC-1");old.setCpldVersion("CPLD-1");old.setPcbVersion("PCB-1");
        when(versions.selectByTenantAndSn(1L,"SN-A")).thenReturn(old);
        var c=command("missing-hardware",7,"SOFT-2");
        service.record(new ParsedLog(c.tenantId(),c.actorId(),c.projectId(),c.requestKey(),c.logKey(),c.logReceivedAt(),c.parserTaskId(),c.parserVersion(),c.sourceFileId(),c.serialNumber(),c.conpVersion(),null,null,null));
        var captured=org.mockito.ArgumentCaptor.forClass(DeviceNetworkVersionDO.class);
        verify(projections).replaceCurrent(captured.capture());
        assertEquals("BASIC-1",captured.getValue().getBootVersion());
        assertEquals("CPLD-1",captured.getValue().getCpldVersion());
        assertEquals("PCB-1",captured.getValue().getPcbVersion());
        assertNull(rows.get(c.requestKey()).getBootVersion());
        assertNull(rows.get(c.requestKey()).getCpldVersion());
        assertNull(rows.get(c.requestKey()).getPcbVersion());
    }
    @Test void semanticMappingUsesObservedBasicBootloaderRatherThanExtended() {
        var snapshot=List.of(
                Map.of("semanticKey","device.software.version","status","OBSERVED","value","SOFT"),
                Map.of("semanticKey","device.hardware.bootloader.basicVersion","status","OBSERVED","value","BASIC"),
                Map.of("semanticKey","device.hardware.bootloader.extendedVersion","status","OBSERVED","value","EXTENDED"),
                Map.of("semanticKey","device.hardware.cpldVersion","status","OBSERVED","value","CPLD"),
                Map.of("semanticKey","device.hardware.pcbVersion","status","OBSERVED","value","PCB"));
        var parsed=cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSemanticFacts.from(Map.of("semanticResult",Map.of("snapshot",snapshot)));
        assertEquals("SOFT",parsed.conpVersion());assertEquals("BASIC",parsed.bootVersion());
        assertEquals("CPLD",parsed.cpldVersion());assertEquals("PCB",parsed.pcbVersion());
        var unobserved=Map.of("semanticKey","device.software.bootloader.basicVersion","status","MISSING","value","GUESS");
        assertNull(cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSemanticFacts.from(Map.of("semanticResult",Map.of("snapshot",List.of(unobserved)))).bootVersion());
        var observed=Map.of("semanticKey","device.software.bootloader.basicVersion","status","OBSERVED","value","BASIC-LEGACY");
        assertEquals("BASIC-LEGACY",cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSemanticFacts.from(Map.of("semanticResult",Map.of("snapshot",List.of(observed)))).bootVersion());
    }
    @Test void currentVersionComesFromLogEvenWhenOldItrEventTimeIsLater() {
        var old=new DeviceNetworkVersionDO();old.setId(71L);old.setSourceSystem("ITR");
        old.setEffectiveFrom(LocalDateTime.of(2030,1,1,0,0));old.setConpVersion("OLD-ITR");
        when(versions.selectByTenantAndSn(1L,"SN-A")).thenReturn(old);
        var result=service.record(command("new",7,"LOG-V2"));
        assertEquals("UPDATED",result.versionStatus());
        verify(scopes).lockAndRevalidate(argThat(q -> Objects.equals(1L,q.expectedScopeVersion())));
        var version=org.mockito.ArgumentCaptor.forClass(DeviceNetworkVersionDO.class);
        verify(projections).replaceCurrent(version.capture());
        assertEquals("DEVICE_LOG",version.getValue().getSourceSystem());assertEquals("LOG-V2",version.getValue().getConpVersion());
        assertNull(version.getValue().getConpSeries());
    }
    @Test void replayIsImmutableAndDoesNotWriteProjectionTwice() {
        var request=command("one",7,"LOG-V1");
        assertEquals(service.record(request),service.record(request));
        verify(projections,times(1)).updateDeviceVersion(any());assertEquals(1,rows.size());
        assertThrows(IllegalArgumentException.class,() -> service.record(command("one",7,"REPLACED")));
    }
    @Test void lateParsingAnOlderLogPreservesHistoryWithoutVersionRegression() {
        service.record(command("new",7,"LOG-V2"));
        var result=service.record(command("old",6,"LOG-V1"));
        assertEquals("HISTORICAL",result.versionStatus());assertEquals(2,rows.size());
        verify(projections,times(1)).updateDeviceVersion(any());
    }
    @Test void archiveSequenceOrdersLogsReceivedInSameMillisecond() {
        var c=command("new",7,"V2");
        service.record(new ParsedLog(c.tenantId(),c.actorId(),c.projectId(),c.requestKey(),"ast-file:22",c.logReceivedAt(),c.parserTaskId(),c.parserVersion(),22L,c.serialNumber(),c.conpVersion(),null,null,null,2L));
        var older=service.record(new ParsedLog(c.tenantId(),c.actorId(),c.projectId(),"old","ast-file:21",c.logReceivedAt(),"task-old",c.parserVersion(),21L,c.serialNumber(),"V1",null,null,null,1L));
        assertEquals("HISTORICAL",older.versionStatus());
        verify(projections,times(1)).updateDeviceVersion(any());
    }
    @Test void wrongProjectSerialOrMissingSerialCannotUpdateAnyDevice() {
        device.setProjectId(20L);
        assertEquals("DEVICE_NOT_IN_PROJECT",service.record(command("wrong",7,"V")).status());
        var c=command("missing",7,"V");
        assertEquals("SERIAL_NOT_IDENTIFIED",service.record(new ParsedLog(c.tenantId(),c.actorId(),c.projectId(),c.requestKey(),c.logKey(),c.logReceivedAt(),c.parserTaskId(),c.parserVersion(),c.sourceFileId(),null,c.conpVersion(),null,null,null)).status());
        verifyNoInteractions(versions,projections);
    }
    @Test void noSoftwareFactLinksEvidenceWithoutInventingVersion() {
        assertEquals("VERSION_NOT_IDENTIFIED",service.record(command("empty",7,null)).versionStatus());
        assertEquals(1,rows.size());verifyNoInteractions(versions,projections);
    }
    @Test void crossTenantAndEmptyWriteScopeFailBeforePersisting() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,() -> service.record(command("foreign",7,"V")));
        TenantContextHolder.setTenantId(1L);
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,1L,Set.of(),Set.of()));
        assertThrows(IllegalArgumentException.class,() -> service.record(command("denied",7,"V")));
        assertTrue(rows.isEmpty());verifyNoInteractions(versions,projections);
    }
    @Test void changedScopeVersionFailsBeforeWriting() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,2L,Set.of(10L),Set.of()));
        assertThrows(IllegalArgumentException.class,() -> service.record(command("scope-change",7,"V")));
        assertTrue(rows.isEmpty());verifyNoInteractions(versions,projections);
    }
    @Test void deviceMoveDuringAssociationIsRejected() {
        var moved=new DeviceDO();moved.setId(91L);moved.setProjectId(20L);moved.setSn("SN-A");
        when(devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(1L,91L))).thenReturn(moved);
        assertThrows(IllegalArgumentException.class,() -> service.record(command("move",7,"V")));
        assertTrue(rows.isEmpty());verifyNoInteractions(versions,projections);
    }
    private ParsedLog command(String key,int day,String version) {
        return new ParsedLog(1L,7L,10L,key,"log:"+key,LocalDateTime.of(2026,10,day,0,0),"task-"+key,"1.3.0",null,"SN-A",version,null,"CPLD-1","PCB-1");
    }
}
