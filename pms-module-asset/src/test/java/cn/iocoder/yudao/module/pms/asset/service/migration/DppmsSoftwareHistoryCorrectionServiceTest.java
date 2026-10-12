package cn.iocoder.yudao.module.pms.asset.service.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.*;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsSoftwareHistoryCorrectionApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsSoftwareHistoryCorrectionServiceTest {
    final DeviceSoftwareObservationMapper legacy=mock(DeviceSoftwareObservationMapper.class);
    final DeviceSoftwareChangeBatchMapper batches=mock(DeviceSoftwareChangeBatchMapper.class);
    final DeviceSoftwareVersionRecordMapper records=mock(DeviceSoftwareVersionRecordMapper.class);
    final DppmsAssetSourceHistoryService history=mock(DppmsAssetSourceHistoryService.class);
    final ProjectDeviceAssignmentGuardApi guard=mock(ProjectDeviceAssignmentGuardApi.class);
    final DppmsSoftwareHistoryCorrectionService service=new DppmsSoftwareHistoryCorrectionService(legacy,batches,records,history,guard);
    DeviceSoftwareObservationDO old;
    Command command;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(guard.validate(any())).thenReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,true,null));
        old=new DeviceSoftwareObservationDO();old.setId(70L);old.setTenantId(1L);old.setProjectId(100L);old.setDeleted(false);
        old.setSourceSystem("DPPMS");old.setSourceProjectKey("10");old.setSourceKey("20");old.setSourceId(20L);old.setSourceProjectId(10L);
        old.setSourceChecksum("proven-row-checksum");old.setBarCode("SN");old.setConp("V");old.setConpBak("");old.setConpChange(0);
        old.setCustomInfo("{\"before\":null}");old.setSourceCreatedAt(LocalDateTime.of(2020,1,1,0,0));old.setCreateTime(LocalDateTime.of(2026,1,1,0,0));old.setUpdater("1");
        var row=new SoftwareObservation(20L,10L,null,null,null,"SN","V",null,null,null,"",0,null,null,null,null,null,null,null,null,null,null,null,
                old.getSourceCreatedAt(),null,null,null,"{\"before\":null}").withMapping(70L,null,99L);
        command=new Command(1L,1L,100L,"10","capture",LocalDateTime.of(2026,1,1,0,0),List.of(row),List.of());
        when(legacy.selectLegacyForUpdate(any())).thenReturn(old);
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void anyAuditChangeAfterPreviewBlocksBeforeFirstOwnerWrite() {
        var preview=service.preview(command);old.setUpdater("human-editor");
        assertThrows(IllegalArgumentException.class,()->service.correct(new Correction(command,preview)));
        verifyNoInteractions(history);verify(batches,never()).correctHistoricalAudit(any());
    }
    @Test void anyTechnicalMirrorChangeAfterPreviewIsAlsoProtected() {
        var preview=service.preview(command);old.setSourceReadAt(LocalDateTime.now());
        assertThrows(IllegalArgumentException.class,()->service.correct(new Correction(command,preview)));verifyNoInteractions(history);
    }
    @Test void originalNullAndEmptyDifferenceCannotBeSilentlyDropped() {
        old.setConpBak(null);assertThrows(IllegalArgumentException.class,()->service.preview(command));verifyNoInteractions(history);
    }
    @Test void sourceJsonNullAndMissingKeyAreDifferent() {
        old.setCustomInfo("{}");assertThrows(IllegalArgumentException.class,()->service.preview(command));verifyNoInteractions(history);
    }
    @Test void matchingCompletePreviewKeepsExistingIdAndDelegatesOnlyTheProvenRecord() {
        when(history.importCorrectedObservation(eq(command),any())).thenReturn(NativeEntityMigrationRow.mapped("software-versions","20",
                new ExternalTargetMapping("AST","SoftwareVersionRecord","ast_device_software_version_record",70L,"SOFTWARE_VERSION_RECORD",0)));
        var preview=service.preview(command);var result=service.correct(new Correction(command,preview));
        assertEquals(70L,result.getFirst().targets().getFirst().targetId());
        verify(history).importCorrectedObservation(eq(command),argThat(row->row.mappedRecordId().equals(70L) && row.extensionDefinitionId().equals(99L)));
    }
    @Test void tenantMismatchCannotReachGuardOrLegacyTables() {
        clearInvocations(guard,legacy);TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.preview(command));verifyNoInteractions(guard,legacy,history);
    }
    @Test void existingFormalIdCannotBeOverwrittenByCorrection() {
        when(records.selectForUpdate(any())).thenReturn(new DeviceSoftwareVersionRecordDO());
        var failure=assertThrows(IllegalArgumentException.class,()->service.preview(command));
        assertEquals("DPPMS_SOFTWARE_CORRECTION_TARGET_ALREADY_EXISTS",failure.getMessage());
        verifyNoInteractions(history,legacy,batches);
    }
    @Test void missingDuplicateOrExtraBaselineDoesNotAuthorizeMutation() {
        assertThrows(IllegalArgumentException.class,()->service.correct(new Correction(command,List.of())));
        var baseline=service.preview(command).getFirst();
        assertThrows(IllegalArgumentException.class,()->service.correct(new Correction(command,List.of(baseline,baseline))));verifyNoInteractions(history);
    }
}
