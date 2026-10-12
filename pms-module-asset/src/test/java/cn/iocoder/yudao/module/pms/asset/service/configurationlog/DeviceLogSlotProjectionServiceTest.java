package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceAssemblyDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper;
import cn.iocoder.yudao.module.pms.asset.service.assembly.DeviceAssemblyService;
import cn.iocoder.yudao.module.pms.asset.service.assembly.command.ApplyDeviceAssemblyCommand;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceLogSlotProjectionServiceTest {
    final DeviceMapper devices=mock(DeviceMapper.class);
    final DeviceAssemblyMapper edges=mock(DeviceAssemblyMapper.class);
    final DeviceAssemblyService assembly=mock(DeviceAssemblyService.class);
    final DeviceLogParsedFactService facts=mock(DeviceLogParsedFactService.class);
    final DeviceOrganizationProjectionApi organizations=mock(DeviceOrganizationProjectionApi.class);
    final DeviceLogSlotProjectionService service=new DeviceLogSlotProjectionService(devices,edges,assembly,facts,organizations);
    final Map<String,DeviceDO> masters=new HashMap<>();
    final LocalDateTime time=LocalDateTime.of(2026,10,9,10,0);
    final DeviceLogParsedFactApi.ParsedLog parent=new DeviceLogParsedFactApi.ParsedLog(1L,7L,10L,"request","ast-file:90",time,"task","1.3.2",90L,"ROOT","SOFT-P","BOOT-P","C-P","PCB-P",8L);
    final DeviceLogParsedFactApi.Outcome linked=new DeviceLogParsedFactApi.Outcome("LINKED",1L,"ROOT","UPDATED",null);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);master("ROOT",1L,10L);master("CHILD",2L,null);
        when(devices.selectByTenantAndSn(eq(1L),anyString())).thenAnswer(i -> masters.get(i.getArgument(1)));
        when(devices.selectLogDeviceForUpdate(any())).thenAnswer(i -> {
            var query=(cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery)i.getArgument(0);
            return masters.values().stream().filter(d -> d.getId().equals(query.deviceId())).findFirst().orElse(null);
        });
        when(devices.insert(any(DeviceDO.class))).thenAnswer(i -> { DeviceDO d=i.getArgument(0);masters.put(d.getSn(),d);return 1; });
        when(edges.lockLogConflicts(any())).thenReturn(List.of());
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    DeviceDO master(String sn,Long id,Long project) {var d=new DeviceDO();d.setSn(sn);d.setId(id);d.setTenantId(1L);d.setProjectId(project);masters.put(sn,d);return d;}
    DeviceLogSlotFacts slots(String sn,String software) {return new DeviceLogSlotFacts(List.of(new DeviceLogSlotFacts.SlotDevice(4,sn,"BOARD","FW","A","1.00",software,null)));}
    @Test void unknownChildIsLogSourceAndInheritsMissingVersionsWithoutInventingOwnership() {
        service.record(parent,slots("NEW",null),linked);
        var created=masters.get("NEW");assertNotNull(created);assertNull(created.getProjectId());
        assertEquals("DEVICE_LOG",created.getSourceSystem());assertEquals("BOARD",created.getProductModel());
        assertEquals("PENDING_RECONCILIATION",created.getSyncStatus());
        var captured=ArgumentCaptor.forClass(DeviceLogParsedFactApi.ParsedLog.class);
        verify(facts).recordSlot(captured.capture(),eq(1L),eq("SLOT_4"));
        var child=captured.getValue();assertEquals("SOFT-P",child.conpVersion());assertEquals("BOOT-P",child.bootVersion());
        assertEquals("A",child.pcbVersion());assertEquals("1.00",child.cpldVersion());assertNull(child.logVersion());
        assertEquals(90L,child.sourceFileId());assertTrue(child.logKey().contains(":software-parent:boot-parent:cpld-child:pcb-child"));
    }
    @Test void independentSoftwareWinsAndOnlyObservedSlotIsApplied() {
        service.record(parent,slots("CHILD","SOFT-C"),linked);
        var captured=ArgumentCaptor.forClass(DeviceLogParsedFactApi.ParsedLog.class);
        verify(facts).recordSlot(captured.capture(),eq(1L),eq("SLOT_4"));assertEquals("SOFT-C",captured.getValue().conpVersion());
        verify(assembly,times(1)).apply(any());verifyNoMoreInteractions(assembly);verify(devices,never()).insert(any(DeviceDO.class));
        assertNull(masters.get("CHILD").getProjectId());
    }
    @Test void fourVersionsIndependentlyPreferChildAndFillOnlyMissingFields() {
        var mixed=new DeviceLogSlotFacts(List.of(new DeviceLogSlotFacts.SlotDevice(4,"CHILD","BOARD","FW",null,null,"SOFT-C","BOOT-C")));
        service.record(parent,mixed,linked);
        var captured=ArgumentCaptor.forClass(DeviceLogParsedFactApi.ParsedLog.class);
        verify(facts).recordSlot(captured.capture(),eq(1L),eq("SLOT_4"));var child=captured.getValue();
        assertEquals("SOFT-C",child.conpVersion());assertEquals("BOOT-C",child.bootVersion());
        assertEquals("C-P",child.cpldVersion());assertEquals("PCB-P",child.pcbVersion());
        assertTrue(child.logKey().contains(":software-child:boot-child:cpld-parent:pcb-parent"));
    }
    @Test void conflictingRelationshipUsesReplacementServiceAndRetainsSourceEvidence() {
        master("OLD",3L,10L);var edge=new DeviceAssemblyDO();edge.setParentDeviceSn("OLD");edge.setChildDeviceSn("CHILD");edge.setEffectiveFrom(time.minusDays(1));
        when(edges.lockLogConflicts(any())).thenReturn(List.of(edge));service.record(parent,slots("CHILD",null),linked);
        var captured=ArgumentCaptor.forClass(ApplyDeviceAssemblyCommand.class);verify(assembly).apply(captured.capture());
        assertEquals("ROOT",captured.getValue().parentDeviceSn());assertEquals("ast-file:90",captured.getValue().evidenceRef());
    }
    @Test void childInOtherProjectCannotBeReassignedOrProjected() {
        masters.get("CHILD").setProjectId(11L);
        assertThrows(IllegalArgumentException.class,()->service.record(parent,slots("CHILD",null),linked));verifyNoInteractions(assembly,facts);
    }
    @Test void conflictOwnedByOtherProjectCannotBeReplaced() {
        master("OLD",3L,11L);var edge=new DeviceAssemblyDO();edge.setParentDeviceSn("OLD");edge.setChildDeviceSn("CHILD");edge.setEffectiveFrom(time.minusDays(1));
        when(edges.lockLogConflicts(any())).thenReturn(List.of(edge));
        assertThrows(IllegalArgumentException.class,()->service.record(parent,slots("CHILD",null),linked));verifyNoInteractions(assembly,facts);
    }
    @Test void olderEvidenceCannotCloseNewerAssemblyInterval() {
        var edge=new DeviceAssemblyDO();edge.setEffectiveFrom(time.plusDays(1));when(edges.lockLogConflicts(any())).thenReturn(List.of(edge));
        assertThrows(IllegalArgumentException.class,()->service.record(parent,slots("CHILD",null),linked));verifyNoInteractions(assembly,facts);
    }
    @Test void historicalParentAndEmptyLogHaveNoAssemblySideEffects() {
        service.record(parent,slots("CHILD",null),new DeviceLogParsedFactApi.Outcome("LINKED",1L,"ROOT","HISTORICAL",null));
        service.record(parent,new DeviceLogSlotFacts(List.of()),linked);verifyNoInteractions(devices,edges,assembly,facts,organizations);
    }
    @Test void foreignTenantIsRejected() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.record(parent,slots("CHILD",null),linked));
        verifyNoInteractions(devices,edges,assembly,facts,organizations);
    }
}
