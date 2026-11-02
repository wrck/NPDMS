package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assembly.DeviceSourceRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceSourceRelationshipMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.commerce.api.scope.ProjectContractQueryApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsDeviceSourceRelationshipServiceTest {
    final DeviceSourceRelationshipMapper mapper=mock(DeviceSourceRelationshipMapper.class);
    final DeviceMapper devices=mock(DeviceMapper.class);
    final DeviceAccessScopeService scope=mock(DeviceAccessScopeService.class);
    final ProjectContractQueryApi contracts=mock(ProjectContractQueryApi.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final DppmsDeviceSourceRelationshipService service=new DppmsDeviceSourceRelationshipService(mapper,devices,scope,contracts,permissions);
    final Relation source=new Relation(20L,"MAIN","P1","EXTRA","P2","CONTRACT","2023-01-02 03:04:05.1230000",null);
    final Command command=new Command(1L,7L,List.of(source));
    DeviceSourceRelationshipDO inserted;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(7L,"pms:integration:execute")).thenReturn(true);
        when(mapper.insertHistorical(any())).thenAnswer(call->{
            inserted=call.getArgument(0,DeviceSourceRelationshipMapper.HistoricalInsert.class).row();return 1;
        });
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void previewValidatesOriginalHistoryWithoutAssigningIdOrWriting() {
        var result=service.previewRelationships(command).getFirst();
        assertEquals("CREATE",result.action());assertNull(result.after().id());assertNull(result.before());
        assertEquals(LocalDateTime.parse("2023-01-02T03:04:05.123"),result.after().createTime());
        verify(mapper,never()).insertHistorical(any());
    }
    @Test void realEntityPreservesOriginalDatesAndNullUpdateThenReplaysWithoutChangingActor() {
        var result=service.importRelationships(command);
        assertEquals("ast_device_relationship",result.getFirst().targets().getFirst().targetTable());
        assertEquals(LocalDateTime.of(2023,1,2,3,4,5,123000000),inserted.getCreateTime());
        assertEquals(inserted.getCreateTime(),inserted.getEffectiveFrom());
        assertEquals(inserted.getCreateTime(),inserted.getUpdateTime());
        assertNull(inserted.getSourceVersion());assertEquals("7",inserted.getCreator());
        when(mapper.selectSourceForUpdate(any())).thenReturn(inserted);
        when(permissions.hasAnyPermissions(8L,"pms:integration:execute")).thenReturn(true);
        service.importRelationships(new Command(1L,8L,List.of(source)));
    }
    @Test void replayPreservesExistingAuditActorsAndVersion() {
        service.importRelationships(command);inserted.setCreator("migration-original");inserted.setUpdater("operator");
        inserted.setVersion(4);when(mapper.selectSourceForUpdate(any())).thenReturn(inserted);
        service.importRelationships(command);
        assertEquals("migration-original",inserted.getCreator());assertEquals(4,inserted.getVersion());
        verify(mapper,times(1)).insertHistorical(any());
    }
    @Test void auditDriftRequiresSeparatelyApprovedCorrection() {
        service.importRelationships(command);inserted.setCreateTime(LocalDateTime.now());
        when(mapper.selectSourceForUpdate(any())).thenReturn(inserted);
        var ex=assertThrows(IllegalArgumentException.class,()->service.importRelationships(command));
        assertTrue(ex.getMessage().contains("AUDIT_REQUIRES_CORRECTION"));
        verify(mapper,times(1)).insertHistorical(any());
    }
    @Test void changedFactOrDeletedIdentityNeverGetsOverwritten() {
        service.importRelationships(command);inserted.setTargetItem("MANUAL");
        when(mapper.selectSourceForUpdate(any())).thenReturn(inserted);
        assertThrows(IllegalArgumentException.class,()->service.importRelationships(command));
        inserted.setTargetItem("P2");inserted.setDeleted(true);
        assertThrows(IllegalArgumentException.class,()->service.importRelationships(command));
        verify(mapper,times(1)).insertHistorical(any());
    }
    @Test void entireBatchValidatesSelfRelationBeforeWritingFirstRow() {
        var self=new Relation(21L,"S","P","S","P","C",source.sourceCreateTime(),null);
        assertThrows(IllegalArgumentException.class,()->service.importRelationships(new Command(1L,7L,List.of(source,self))));
        verifyNoInteractions(mapper);
    }
    @Test void unknownPrecisionMalformedDateAndDuplicateSourceAreBlocked() {
        assertThrows(IllegalArgumentException.class,()->DppmsDeviceSourceRelationshipService.parseDate("2023-01-02 03:04:05.1230001"));
        assertThrows(IllegalArgumentException.class,()->DppmsDeviceSourceRelationshipService.parseDate("2023-02-31 00:00:00"));
        assertThrows(IllegalArgumentException.class,()->service.importRelationships(new Command(1L,7L,List.of(source,source))));
        verifyNoInteractions(mapper);
    }
    @Test void crossTenantAndNoMigrationPermissionCannotWrite() {
        assertThrows(IllegalArgumentException.class,()->service.importRelationships(new Command(2L,7L,List.of(source))));
        assertThrows(IllegalArgumentException.class,()->service.importRelationships(new Command(1L,9L,List.of(source))));
        verifyNoInteractions(mapper);
    }
    @Test void emptyProjectPermissionAndEmptyContractsDoNotBecomeGlobalQuery() {
        var query=new Query(1L,7L,100L,null,null,1,20);
        when(scope.visibleProjectIds(1L,7L)).thenReturn(Set.of());
        assertEquals(0,service.pageRelationships(query).getTotal());verifyNoInteractions(mapper,contracts);
        when(scope.visibleProjectIds(1L,7L)).thenReturn(Set.of(100L));
        when(contracts.getCurrentContractNumbers(100L)).thenReturn(Set.of());
        assertEquals(0,service.pageRelationships(query).getTotal());verifyNoInteractions(mapper);
    }
    @Test void devicePageUsesVisibleDeviceSnAndRejectsUnrelatedCallerFilter() {
        var device=new DeviceDO();device.setId(90L);device.setTenantId(1L);device.setSn("MAIN");device.setDeleted(false);
        when(devices.selectByTenantAndId(1L,90L)).thenReturn(device);
        when(mapper.countRelationships(any())).thenReturn(0L);
        service.pageRelationships(new Query(1L,7L,null,90L,null,2,20));
        verify(scope).assertReadable(new cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService.ReadAccess(1L,7L,90L));
        verify(mapper).countRelationships(argThat(q->"MAIN".equals(q.deviceSn())&&q.offset()==20));
        assertThrows(IllegalArgumentException.class,()->service.pageRelationships(new Query(1L,7L,null,90L,"OTHER",1,20)));
        verifyNoInteractions(contracts);
    }
    @Test void ambiguousUnscopedAndCrossTenantPagesAreBlocked() {
        assertThrows(IllegalArgumentException.class,()->service.pageRelationships(new Query(1L,7L,null,null,null,1,20)));
        assertThrows(IllegalArgumentException.class,()->service.pageRelationships(new Query(1L,7L,100L,90L,null,1,20)));
        assertThrows(IllegalArgumentException.class,()->service.pageRelationships(new Query(2L,7L,null,90L,null,1,20)));
        verifyNoInteractions(mapper,devices,scope,contracts);
    }
    AuditRecord auditRecord() {
        return new AuditRecord(inserted.getId(),inserted.getTenantId(),inserted.getSourceDeviceSn(),inserted.getTargetDeviceSn(),
                inserted.getSourceItem(),inserted.getTargetItem(),inserted.getRelationshipType(),inserted.getContractNo(),
                inserted.getEffectiveFrom(),inserted.getEffectiveTo(),inserted.getSourceSystem(),inserted.getSourceKey(),
                inserted.getSourceVersion(),inserted.getVersion(),inserted.getCreator(),inserted.getCreateTime(),
                inserted.getUpdater(),inserted.getUpdateTime(),inserted.getDeleted());
    }
    AuditRequest auditRequest() {
        return new AuditRequest(1L,7L,"integration:1:12",List.of(new AuditEntry(source,inserted.getId(),auditRecord().fingerprint())));
    }
    void auditSetup() {
        service.importRelationships(command);inserted.setCreator("data_sync");inserted.setUpdater("data_sync");
        inserted.setCreateTime(LocalDateTime.of(2026,9,29,12,1,32,544000000));inserted.setUpdateTime(inserted.getCreateTime());
        when(mapper.selectSourceForUpdate(any())).thenReturn(inserted);
        when(mapper.correctHistoricalAudit(any())).thenAnswer(call->{
            var write=call.getArgument(0,DeviceSourceRelationshipMapper.HistoricalAuditWrite.class);
            inserted.setCreateTime(write.createTime());inserted.setUpdateTime(write.updateTime());
            inserted.setVersion(write.expectedVersion()+1);return 1;
        });
    }
    @Test void correctionChangesOnlyTwoDatesAndVersionAndReplays() {
        auditSetup();var before=auditRecord();var request=auditRequest();
        var preview=service.previewAuditCorrection(request).getFirst();assertEquals("UPDATED",preview.action());
        verify(mapper,never()).correctHistoricalAudit(any());
        var result=service.applyAuditCorrection(request).getFirst();assertEquals(preview.after(),result.after());
        assertEquals(before.creator(),result.after().creator());assertEquals(before.updater(),result.after().updater());
        assertEquals(before.effectiveFrom(),result.after().effectiveFrom());assertEquals(before.id(),result.after().id());
        assertEquals(1,result.after().version());assertEquals(result.after().createTime(),result.after().updateTime());
        assertEquals("UNCHANGED",service.applyAuditCorrection(auditRequest()).getFirst().action());
        verify(mapper,times(1)).correctHistoricalAudit(any());
    }
    @Test void staleFingerprintBlocksChangesToEveryPhysicalField() {
        auditSetup();var request=auditRequest();
        var mutations=List.<Runnable>of(()->inserted.setCreator("manual"),()->inserted.setUpdater("manual"),
                ()->inserted.setUpdateTime(LocalDateTime.now()),()->inserted.setCreateTime(LocalDateTime.now()),
                ()->inserted.setVersion(1),()->inserted.setSourceItem("CHANGED"),()->inserted.setDeleted(true));
        for(var mutation:mutations) {
            inserted.setCreator("data_sync");inserted.setUpdater("data_sync");inserted.setVersion(0);inserted.setSourceItem("P1");
            inserted.setDeleted(false);inserted.setCreateTime(LocalDateTime.of(2026,9,29,12,1,32,544000000));inserted.setUpdateTime(inserted.getCreateTime());
            mutation.run();assertThrows(IllegalArgumentException.class,()->service.applyAuditCorrection(request));
        }
        verify(mapper,never()).correctHistoricalAudit(any());
    }
    @Test void wholeCorrectionChunkMustValidateBeforeFirstWrite() {
        auditSetup();var first=auditRequest().entries().getFirst();
        var second=new AuditEntry(new Relation(21L,"S","P","S","P","C",source.sourceCreateTime(),null),21L,"0".repeat(64));
        assertThrows(IllegalArgumentException.class,()->service.applyAuditCorrection(new AuditRequest(1L,7L,"integration:1:12",List.of(first,second))));
        verify(mapper,never()).correctHistoricalAudit(any());
    }
    @Test void correctionRejectsForeignTenantPermissionAndUnscopedOwner() {
        auditSetup();var entries=auditRequest().entries();
        assertThrows(IllegalArgumentException.class,()->service.applyAuditCorrection(new AuditRequest(2L,7L,"integration:2:12",entries)));
        assertThrows(IllegalArgumentException.class,()->service.applyAuditCorrection(new AuditRequest(1L,9L,"integration:1:12",entries)));
        assertThrows(IllegalArgumentException.class,()->service.applyAuditCorrection(new AuditRequest(1L,7L,"manual",entries)));
        verify(mapper,never()).correctHistoricalAudit(any());
    }
    @Test void failedConditionalCorrectionDoesNotReturnSuccess() {
        auditSetup();doReturn(0).when(mapper).correctHistoricalAudit(any());
        assertThrows(IllegalArgumentException.class,()->service.applyAuditCorrection(auditRequest()));
    }
}
