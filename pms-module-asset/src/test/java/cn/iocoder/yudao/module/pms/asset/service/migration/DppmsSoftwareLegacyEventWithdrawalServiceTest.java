package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareLegacyEventWithdrawalApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsSoftwareLegacyEventWithdrawalServiceTest {
    final DeviceNetworkVersionEventMapper events=mock(DeviceNetworkVersionEventMapper.class);
    final DeviceSoftwareVersionRecordMapper records=mock(DeviceSoftwareVersionRecordMapper.class);
    final DeviceSoftwareChangeBatchMapper batches=mock(DeviceSoftwareChangeBatchMapper.class);
    final DppmsAssetSourceHistoryApi history=mock(DppmsAssetSourceHistoryApi.class);
    final ProjectDeviceAssignmentGuardApi projects=mock(ProjectDeviceAssignmentGuardApi.class);
    final DppmsSoftwareLegacyEventWithdrawalService service=new DppmsSoftwareLegacyEventWithdrawalService(events,records,batches,history,projects);
    final Identity identity=new Identity(20L,80L,70L);
    DppmsAssetSourceHistoryApi.Command source;
    DeviceSoftwareLegacyEventWithdrawalDO row;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var observation=JsonUtils.parseObject("{\"sourceId\":20,\"sourceProjectId\":10,\"barCode\":\"SN\",\"mappedRecordId\":70}",DppmsAssetSourceHistoryApi.SoftwareObservation.class);
        source=new DppmsAssetSourceHistoryApi.Command(1L,7L,100L,"10","capture",LocalDateTime.of(2026,1,1,0,0),List.of(observation),List.of(),List.of());
        when(projects.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        when(records.selectForUpdate(any())).thenReturn(new DeviceSoftwareVersionRecordDO());
        row=new DeviceSoftwareLegacyEventWithdrawalDO();row.setId(80L);row.setTenantId(1L);row.setSourceSystem("DPPMS");
        row.setSourceDeviceKey("SN");row.setDeviceSn("SN");row.setSourceEventKey("pm_project_soft_version:20");row.setDeleted(false);
        row.setCustomInfo("{\"before\":null}");row.setSourceLogKey("30");row.setSourceDataState(0);
        when(events.selectLegacyWithdrawalForUpdate(any())).thenReturn(row);when(events.withdrawLegacyEvent(any())).thenReturn(1);
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void completeOriginalEventIsCheckedBeforeTheOwnerDeletion() {
        var baseline=service.preview(source,List.of(identity));
        service.withdraw(new Withdrawal(source,baseline));
        verify(events).withdrawLegacyEvent(argThat(query->query.tenantId()==1L && query.eventId()==80L
                && query.sourceEventKey().equals("pm_project_soft_version:20") && query.sourceDeviceKey().equals("SN")));
        verify(history,times(2)).importSourceHistory(source);
    }
    @Test void previouslyUnmodeledReleaseDateChangeBlocksBeforeDeletingAnything() {
        var baseline=service.preview(source,List.of(identity));row.setReleaseDate(LocalDate.of(2025,1,1));
        var failure=assertThrows(IllegalArgumentException.class,()->service.withdraw(new Withdrawal(source,baseline)));
        assertEquals("DPPMS_SOFTWARE_EVENT_WITHDRAWAL_EVENT_CHANGED",failure.getMessage());verify(events,never()).withdrawLegacyEvent(any());
    }
    @Test void originalJsonNullCannotBecomeAnEmptyObject() {
        var baseline=service.preview(source,List.of(identity));row.setCustomInfo("{}");
        assertThrows(IllegalArgumentException.class,()->service.withdraw(new Withdrawal(source,baseline)));verify(events,never()).withdrawLegacyEvent(any());
    }
    @Test void nativeEventIdentityCannotBeWithdrawnAsDppms() {
        row.setSourceSystem("DEVICE_LOG");assertThrows(IllegalArgumentException.class,()->service.preview(source,List.of(identity)));
        verify(events,never()).withdrawLegacyEvent(any());
    }
    @Test void foreignTenantAndDeniedProjectNeverReachHistoryOrEventRows() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.preview(source,List.of(identity)));
        verifyNoInteractions(projects,history,records,events);
        TenantContextHolder.setTenantId(1L);when(projects.validate(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->service.preview(source,List.of(identity)));verifyNoInteractions(history,records,events);
    }
    @Test void incompleteFormalCarryOrChangedFormalHistoryBlocksWithdrawal() {
        when(records.selectForUpdate(any())).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.preview(source,List.of(identity)));
        verifyNoInteractions(history,events);
        when(records.selectForUpdate(any())).thenReturn(new DeviceSoftwareVersionRecordDO());
        doThrow(new IllegalArgumentException("FORMAL_CHANGED")).when(history).importSourceHistory(source);
        assertThrows(IllegalArgumentException.class,()->service.preview(source,List.of(identity)));verifyNoInteractions(events);
    }
    @Test void duplicateAndUnboundIdentitiesNeverReachDeletion() {
        assertThrows(IllegalArgumentException.class,()->service.preview(source,List.of(identity,identity)));
        assertThrows(IllegalArgumentException.class,()->service.preview(source,List.of(new Identity(20L,80L,71L))));
        verify(events,never()).withdrawLegacyEvent(any());
    }
    @Test void deletionFailureDoesNotProduceACompletedOwnerResult() {
        var baseline=service.preview(source,List.of(identity));when(events.withdrawLegacyEvent(any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class,()->service.withdraw(new Withdrawal(source,baseline)));
    }
    @Test void replayStillVerifiesFormalCarryAndRejectsAnyReappearingEvent() {
        when(events.selectLegacyWithdrawalForUpdate(any())).thenReturn(null);service.verifyWithdrawn(source,List.of(identity));
        verify(history).importSourceHistory(source);verify(events,never()).withdrawLegacyEvent(any());
        when(events.selectSourceIdentity(any())).thenReturn(new DeviceNetworkVersionEventDO());
        assertThrows(IllegalArgumentException.class,()->service.verifyWithdrawn(source,List.of(identity)));
    }
}
