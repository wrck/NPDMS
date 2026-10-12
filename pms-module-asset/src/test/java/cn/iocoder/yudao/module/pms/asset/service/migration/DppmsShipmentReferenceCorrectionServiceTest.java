package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsShipmentReferenceCorrectionServiceTest {
    final ShipmentReferenceCorrectionMapper mapper=mock(ShipmentReferenceCorrectionMapper.class);
    final DeviceShipmentMapper shipments=mock(DeviceShipmentMapper.class);
    final ProjectDeviceAssignmentGuardApi projects=mock(ProjectDeviceAssignmentGuardApi.class);
    final DppmsShipmentReferenceCorrectionService service=new DppmsShipmentReferenceCorrectionService(mapper,shipments,projects);
    final Reference reference=new Reference("20",80L,70L,90L,"sn","ORDER","10625024");
    final Command command=new Command(1L,7L,100L,List.of(reference));
    DeviceShipmentDO shipment;DeviceDO device;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(projects.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        shipment=new DeviceShipmentDO();shipment.setId(80L);shipment.setTenantId(1L);shipment.setDeleted(false);
        shipment.setSourceSystem("DPPMS");shipment.setSourceKey("20");shipment.setDeviceSn("SN");
        shipment.setOrderNo("ORDER");shipment.setLineNo("10625024");shipment.setVersion(3);
        shipment.setShipmentTime(LocalDateTime.of(2020,1,1,0,0));shipment.setPackageNo("PACKAGE");shipment.setContractNo("CONTRACT");
        shipment.setSourcePayload("{\"id\":20}");
        device=new DeviceDO();device.setId(70L);device.setTenantId(1L);device.setDeleted(false);device.setProjectId(100L);
        device.setSn("sn");device.setShipmentRecordId(60L);device.setVersion(4L);
        device.setShipmentTime(shipment.getShipmentTime());device.setPackageNo("PACKAGE");device.setContractNo("CONTRACT");
        when(mapper.selectShipmentForUpdate(any())).thenAnswer(call->{
            var query=call.getArgument(0,cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.ShipmentReferenceRecordQuery.class);
            return query!=null && query.id()==80L?shipment:null;
        });
        when(mapper.selectDeviceForUpdate(any())).thenReturn(device);when(shipments.selectLatest(any())).thenReturn(shipment);
        when(mapper.fillOrderLine(any())).thenAnswer(call->{shipment.setOrderLineId(90L);return 1;});
        when(mapper.repairDeviceShipment(any())).thenAnswer(call->{device.setShipmentRecordId(80L);return 1;});
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void limitedRepairPreservesEveryOtherFieldAndNullAuditThenReplays() {
        var baseline=service.preview(command);service.correct(command,baseline);service.verify(command,baseline);
        assertEquals(90L,shipment.getOrderLineId());assertEquals(80L,device.getShipmentRecordId());
        assertEquals(3,shipment.getVersion());assertEquals(4L,device.getVersion());
        assertNull(shipment.getUpdater());assertNull(device.getUpdater());assertEquals("10625024",shipment.getLineNo());
        verify(mapper,times(1)).fillOrderLine(any());verify(mapper,times(1)).repairDeviceShipment(any());
    }
    @Test void originalPayloadChangeBlocksBeforeAnyWrite() {
        var baseline=service.preview(command);shipment.setSourcePayload("{}");
        assertThrows(IllegalArgumentException.class,()->service.correct(command,baseline));neverWrite();
    }
    @Test void productionLongWireSerializationAlsoPreservesTheCompleteAppliedBaseline() {
        var originalMapper=cn.iocoder.yudao.framework.common.util.json.JsonUtils.getObjectMapper();
        try {
            var module=new tools.jackson.databind.module.SimpleModule();
            module.addSerializer(Long.class,cn.iocoder.yudao.framework.common.util.json.databind.NumberSerializer.INSTANCE);
            cn.iocoder.yudao.framework.common.util.json.JsonUtils.init(originalMapper.rebuild().addModule(module).build());
            var bigReference=new Reference("20",80L,70L,2101600914900267749L,"sn","ORDER","10625024");
            var bigCommand=new Command(1L,7L,100L,List.of(bigReference));
            doAnswer(call->{shipment.setOrderLineId(bigReference.orderLineId());return 1;}).when(mapper).fillOrderLine(any());
            var baseline=service.preview(bigCommand);service.correct(bigCommand,baseline);service.verify(bigCommand,baseline);
            assertEquals(bigReference.orderLineId(),shipment.getOrderLineId());
        } finally {cn.iocoder.yudao.framework.common.util.json.JsonUtils.init(originalMapper);}
    }
    @Test void laterAuditOrLocationChangeBlocksBeforeAnyWrite() {
        var baseline=service.preview(command);device.setLocationSnapshot("human update");
        assertThrows(IllegalArgumentException.class,()->service.correct(command,baseline));neverWrite();
    }
    @Test void nullBusinessFieldCannotSilentlyBecomeBlank() {
        var baseline=service.preview(command);shipment.setSecondaryItem("");
        assertEquals("DPPMS_SHIPMENT_REFERENCE_TARGET_CHANGED",
                assertThrows(IllegalArgumentException.class,()->service.correct(command,baseline)).getMessage());neverWrite();
    }
    @Test void nullAuditCannotBeReplacedWithAnAdministrator() {
        var baseline=service.preview(command);shipment.setUpdater("1");
        assertThrows(IllegalArgumentException.class,()->service.correct(command,baseline));neverWrite();
    }
    @Test void replayChecksAllFieldsAfterRepairAndDoesNotWriteAgain() {
        var baseline=service.preview(command);service.correct(command,baseline);device.setRemark("later change");
        assertThrows(IllegalArgumentException.class,()->service.verify(command,baseline));
        verify(mapper,times(1)).fillOrderLine(any());verify(mapper,times(1)).repairDeviceShipment(any());
    }
    @Test void currentExistingShipmentIsNotReplacedByAHistoricalCandidate() {
        when(mapper.selectShipmentForUpdate(argThat(q->q!=null && q.id()==60L))).thenReturn(new DeviceShipmentDO());
        assertThrows(IllegalArgumentException.class,()->service.preview(command));neverWrite();
    }
    @Test void conflictingOrderLineCannotBeOverwritten() {
        shipment.setOrderLineId(91L);assertThrows(IllegalArgumentException.class,()->service.preview(command));neverWrite();
    }
    @Test void laterShipmentOrDifferentProjectionBlocksBeforeRepair() {
        var latest=new DeviceShipmentDO();latest.setId(81L);when(shipments.selectLatest(any())).thenReturn(latest);
        assertThrows(IllegalArgumentException.class,()->service.preview(command));neverWrite();
        when(shipments.selectLatest(any())).thenReturn(shipment);device.setShipmentTime(shipment.getShipmentTime().plusDays(1));
        assertThrows(IllegalArgumentException.class,()->service.preview(command));neverWrite();
    }
    @Test void tenantAndProjectPermissionCannotBeBypassed() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.preview(command));
        verifyNoInteractions(mapper,shipments,projects);
        TenantContextHolder.setTenantId(1L);when(projects.validate(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->service.preview(command));verifyNoInteractions(mapper,shipments);
    }
    @Test void deletedOrForeignProjectDevicesAreRejected() {
        device.setDeleted(true);assertThrows(IllegalArgumentException.class,()->service.preview(command));neverWrite();
        device.setDeleted(false);device.setProjectId(101L);assertThrows(IllegalArgumentException.class,()->service.preview(command));neverWrite();
    }
    @Test void clientBaselineCannotReplaceTheVerifiedReference() {
        var input=List.of(new Baseline(new Reference("20",81L,70L,90L,"sn","ORDER","10625024"),"{}","{}"));
        assertThrows(IllegalArgumentException.class,()->service.correct(command,input));neverWrite();
    }
    @Test void duplicateIdentitiesNeverReachRowLocks() {
        assertThrows(IllegalArgumentException.class,()->service.preview(new Command(1L,7L,100L,List.of(reference,reference))));
        verifyNoInteractions(mapper,shipments);
    }
    @Test void zeroUpdateCountsAreFailuresAndDoNotContinueToTheDevice() {
        var baseline=service.preview(command);doReturn(0).when(mapper).fillOrderLine(any());
        assertEquals("DPPMS_SHIPMENT_REFERENCE_ORDER_LINE_WRITE_FAILED",
                assertThrows(IllegalArgumentException.class,()->service.correct(command,baseline)).getMessage());
        verify(mapper,never()).repairDeviceShipment(any());
    }
    @Test void aDeviceWriteFailureCannotReturnSuccessfulRepair() {
        var baseline=service.preview(command);doReturn(0).when(mapper).repairDeviceShipment(any());
        assertEquals("DPPMS_SHIPMENT_REFERENCE_DEVICE_REFERENCE_WRITE_FAILED",
                assertThrows(IllegalArgumentException.class,()->service.correct(command,baseline)).getMessage());
    }
    private void neverWrite(){verify(mapper,never()).fillOrderLine(any());verify(mapper,never()).repairDeviceShipment(any());}
}
