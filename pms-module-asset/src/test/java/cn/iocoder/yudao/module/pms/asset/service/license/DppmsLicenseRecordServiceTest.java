package cn.iocoder.yudao.module.pms.asset.service.license;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.license.DeviceLicenseRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.license.DeviceLicenseRecordMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DppmsLicenseRecordServiceTest {
    final DeviceLicenseRecordMapper mapper=mock(DeviceLicenseRecordMapper.class);
    final DeviceMapper devices=mock(DeviceMapper.class);
    final ProjectDeviceAssignmentGuardApi projects=mock(ProjectDeviceAssignmentGuardApi.class);
    final cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi scopes=mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
    final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    final PlatformMigrationEvidenceApi evidence=mock(PlatformMigrationEvidenceApi.class);
    final DeviceLicenseRecordCipher cipher=new DeviceLicenseRecordCipher(DeviceLicenseRecordCipherTest.KEY,"TEST-V1");
    final DppmsLicenseRecordService service=new DppmsLicenseRecordService(mapper,devices,cipher,projects,scopes,commands,evidence);
    final Map<Long,DeviceLicenseRecordDO> stored=new HashMap<>();
    final Map<String,DppmsLicenseRecordService.Saved> receipts=new HashMap<>();
    final Map<String,MigrationSourceRecordFact> facts=new LinkedHashMap<>();
    final License source=new License("201",null,"  MODEL  ","CONTRACT","CUSTOM","CODE","原状态",null,DeviceLicenseRecordCipherTest.ORIGINAL);
    final Command command=new Command(1L,7L,100L,200L,LocalDateTime.of(2020,1,2,3,4),List.of(source));
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(scopes.resolveCurrent(any())).thenAnswer(inv->{
            var query=inv.getArgument(0,cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery.class);
            return new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(query.anchorProjectId(),1L,Set.of(query.anchorProjectId()),Set.of());
        });
        when(projects.validate(any())).thenAnswer(inv->{
            var query=inv.getArgument(0,cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery.class);
            return new ProjectDeviceAssignmentGuardResult(query.projectId(),query.tenantId(),null,query.projectId(),1L,true,null);
        });
        when(mapper.insert(any(DeviceLicenseRecordDO.class))).thenAnswer(inv->{
            var row=inv.getArgument(0,DeviceLicenseRecordDO.class);var copy=new DeviceLicenseRecordDO();
            org.springframework.beans.BeanUtils.copyProperties(row,copy);stored.put(copy.getId(),copy);return 1;
        });
        when(mapper.selectRecordForUpdate(any())).thenAnswer(inv->stored.get(inv.getArgument(0,DeviceLicenseRecordMapper.RecordQuery.class).id()));
        when(commands.execute(any(),anyString(),eq(DppmsLicenseRecordService.Saved.class),any(),any())).thenAnswer(inv->{
            var scope=inv.getArgument(0,IdempotencyScope.class);var previous=receipts.get(scope.key());
            if(previous!=null)return new ExecutionResult<>(previous.checksum().equals(inv.getArgument(1))?Decision.REPLAY_COMPLETED:Decision.CONFLICT,previous);
            var saved=(DppmsLicenseRecordService.Saved)inv.getArgument(3,Supplier.class).get();receipts.put(scope.key(),saved);
            return new ExecutionResult<>(Decision.NEW,saved);
        });
        when(evidence.preview(any())).thenAnswer(inv->{
            var q=inv.getArgument(0,MigrationPreviewCommand.class);var items=new ArrayList<MigrationPreviewItem>();
            for(var request:q.records()) {
                var fact=facts.get(request.sourcePk());var saved=fact==null?null:JsonUtils.parseObject(fact.sourcePayloadJson(),DppmsLicenseRecordService.Saved.class);
                var targets=saved==null?List.<MigrationPreviewTarget>of():List.of(new MigrationPreviewTarget("MAPPED","AST","DeviceLicenseRecord","ast_device_license_record",saved.id(),"LICENSE_HISTORY",0,"test"));
                items.add(new MigrationPreviewItem(request.sourcePk(),saved==null?MigrationPreviewClassification.NO_EXISTING_SOURCE:
                    saved.checksum().equals(request.sourceChecksum())?MigrationPreviewClassification.MAPPED:MigrationPreviewClassification.CHECKSUM_CONFLICT,
                    fact==null?null:fact.sourceRecordId(),fact==null?null:fact.batchId(),fact==null?null:fact.sourceChecksum(),targets));
            }
            return new MigrationPreviewResult("DPPMS",DppmsLicenseRecordService.SOURCE_TABLE,items,0,0,0,0,0,0);
        });
        when(evidence.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(new ArrayList<>(),null));
        var batch=mock(MigrationBatchFact.class);when(batch.batchId()).thenReturn(800L);when(batch.version()).thenReturn(0);
        when(evidence.createImportBatch(any())).thenReturn(batch);
        var claim=mock(MigrationBatchClaimResult.class);when(claim.claimed()).thenReturn(true);when(claim.batch()).thenReturn(batch);
        when(evidence.claimStagedBatch(any())).thenReturn(claim);
        when(evidence.appendSourceRecords(any())).thenAnswer(inv->{
            var q=inv.getArgument(0,AppendMigrationSourceRecordsCommand.class);var result=new ArrayList<MigrationSourceRecordFact>();
            for(var row:q.records()) {var fact=new MigrationSourceRecordFact(900L+facts.size(),1L,800L,"DPPMS",row.sourceTable(),row.sourcePk(),row.sourceBusinessKey(),row.sourcePayloadJson(),row.sourceChecksum(),row.extractedAt(),"MAPPED");facts.put(row.sourcePk(),fact);result.add(fact);}
            when(evidence.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.copyOf(facts.values()),null));return result;
        });
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void fullBusinessFactsCipherAndActualAuditReachOnlyApprovedEntity() {
        assertEquals("CREATE",service.preview(command).getFirst().action());assertTrue(stored.isEmpty());
        var result=service.apply(command);assertEquals(1,result.size());var row=stored.get(result.getFirst().id());
        assertNull(row.getDeviceSn());assertNull(row.getDeviceId());assertNull(row.getContractId());
        assertEquals("  MODEL  ",row.getProductModel());assertEquals("原状态",row.getLicenseStatus());assertEquals("CODE",row.getProductCode());
        assertEquals("7",row.getCreator());assertEquals("7",row.getUpdater());assertNotEquals(command.extractedAt(),row.getCreateTime());
        assertEquals(DeviceLicenseRecordCipherTest.ORIGINAL.codeMac(),cipher.verifyStored(row.getLicenseCodeCiphertext(),1L));
        assertFalse(JsonUtils.toJsonString(row).contains("licenseCodeCiphertext"));assertFalse(row.toString().contains(row.getLicenseCodeCiphertext()));
    }
    @Test void sameSourceReferencedByAnotherProjectUsesTheSameFormalRowAndKeepsAudit() {
        var id=service.apply(command).getFirst().id();String before=DppmsLicenseRecordService.fields(stored.get(id));
        var other=new Command(1L,7L,101L,201L,command.extractedAt(),command.licenses());
        assertEquals("UNCHANGED",service.preview(other).getFirst().action());assertEquals(id,service.apply(other).getFirst().id());
        assertEquals(1,stored.size());assertEquals(before,DppmsLicenseRecordService.fields(stored.get(id)));
        verify(mapper,times(1)).insert(any(DeviceLicenseRecordDO.class));
    }
    @Test void laterBusinessAuditCipherOrDeletionChangesBlockReplays() {
        var id=service.apply(command).getFirst().id();var row=stored.get(id);
        row.setLicenseStatus("manual");assertThrows(IllegalArgumentException.class,()->service.preview(command));
        row.setLicenseStatus("原状态");row.setUpdater("99");assertThrows(IllegalArgumentException.class,()->service.apply(command));
        row.setUpdater("7");row.setLicenseCodeCiphertext("{}");assertThrows(IllegalArgumentException.class,()->service.preview(command));
    }
    @Test void conflictingSourceDoesNotOverwriteOriginalLicense() {
        service.apply(command);var altered=new License("201",null,"OTHER","CONTRACT","CUSTOM","CODE","原状态",null,source.envelope());
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(1L,7L,100L,200L,command.extractedAt(),List.of(altered))));
        verify(mapper,times(1)).insert(any(DeviceLicenseRecordDO.class));
    }
    @Test void tenantScopeDuplicatesAndCipherSubstitutionFailBeforeWrites() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.apply(command));
        TenantContextHolder.setTenantId(1L);
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(1L,7L,100L,200L,command.extractedAt(),List.of(source,source))));
        var bad=new License("202",null,null,null,null,null,null,null,source.envelope());
        assertThrows(IllegalArgumentException.class,()->service.apply(new Command(1L,7L,100L,200L,command.extractedAt(),List.of(bad))));
        verify(mapper,never()).insert(any(DeviceLicenseRecordDO.class));
    }
    @Test void deniedProjectAndInvalidPageNeverExposeRecords() {
        doReturn(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult(100L,1L,Set.of(),Set.of())).when(scopes).resolveCurrent(any());
        assertThrows(IllegalArgumentException.class,()->service.page(new Query(1L,7L,100L,List.of(1L),1,20)));
        verify(mapper,never()).page(any());
        assertThrows(IllegalArgumentException.class,()->service.page(new Query(1L,7L,100L,List.of(),1,101)));
    }
    @Test void projectViewDoesNotRequireDeviceAssignmentPermission() {
        doReturn(new ProjectDeviceAssignmentGuardResult(100L,1L,null,100L,1L,false,"DENIED")).when(projects).validate(any());
        when(mapper.page(any())).thenReturn(cn.iocoder.yudao.framework.common.pojo.PageResult.empty());
        assertEquals(0,service.page(new Query(1L,7L,100L,List.of(),1,20)).getTotal());
        verify(projects,never()).validate(any());
        assertThrows(IllegalArgumentException.class,()->service.apply(command));
    }
}
