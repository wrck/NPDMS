package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectProductConfigurationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectProductConfigurationMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProjectProductConfigurationImportServiceTest {
    ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    ProjectProductConfigurationMapper mapper=mock(ProjectProductConfigurationMapper.class);
    ProjectProductConfigurationImportService service=new ProjectProductConfigurationImportService(access,evidence,mapper);
    ProjectAccessActor actor=new ProjectAccessActor(1L,31L);
    ProjectLegacyRecordDO original,header;ProjectProductConfigurationDO persisted;
    Map<String,String> raw;Map<Long,ProjectProductConfigurationDO> stored=new LinkedHashMap<>();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);raw=new LinkedHashMap<>();ProjectProductConfigurationImportService.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
        raw.putAll(Map.of("projectCode","SOURCE","orderExecNumber","","itemGroup","2","itemCode","CHILD","quantity","3","bomPaths","ROOT,CHILD","level","2","dataSource","CRM"));
        original=row("configured-products","pm_project_product_config_level_info_from_crm","hash:1",raw);
        header=row("project","pm_project_header","101",Map.of("projectId","101","projectCode","SOURCE-0"));
        when(evidence.statuses(eq(1L),eq("101"),eq(20L),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        when(mapper.countAllProjectRecords(any())).thenReturn(0L);
        when(mapper.insert(any(ProjectProductConfigurationDO.class))).thenAnswer(call->{persisted=call.getArgument(0);stored.put(persisted.getId(),persisted);return 1;});
        when(mapper.selectRecordForUpdate(any())).thenAnswer(call->stored.get(call.getArgument(0,cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectProductConfigurationRecordQuery.class).id()));
        when(mapper.selectBusinessRecordsForUpdate(any())).thenAnswer(call->new ArrayList<>(stored.values()));
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    ProjectLegacyRecordDO row(String domain,String table,String key,Map<String,String> values) {
        var r=new ProjectLegacyRecordDO();r.setId(90L);r.setTenantId(1L);r.setSnapshotId(20L);r.setDomainCode(domain);r.setSourceTable(table);r.setSourceKey(key);r.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(values));return r;
    }
    ProjectProductConfigurationImportService.Plan prepare(){return service.prepare(100L,"101",List.of(header),List.of(original),actor);}
    @Test void allBusinessFieldsEmptyStringsNullsAndActualAuditAreCarried() {
        var result=service.apply(prepare(),actor);assertEquals("SOURCE",persisted.getProjectCode());assertEquals("",persisted.getExecutionNo());assertEquals(new BigDecimal("3"),persisted.getQuantity());assertEquals("CRM",persisted.getDataSource());
        assertEquals(2,persisted.getItemGroup());assertNull(persisted.getParentCode());assertEquals("ROOT,CHILD",persisted.getBomPath());assertEquals(2,persisted.getLevel());
        assertEquals("31",persisted.getCreator());assertEquals("31",persisted.getUpdater());assertEquals(persisted.getCreateTime(),persisted.getUpdateTime());assertEquals(0,persisted.getCreateTime().getNano()%1_000_000);
        assertEquals(ProjectProductConfigurationImportService.baseline(persisted),result.baselines().get("hash:1"));
    }
    @Test void originalDuplicateRowsReceiveSeparateEntitiesAndBindings() {
        var duplicate=row("configured-products","pm_project_product_config_level_info_from_crm","hash:2",raw);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of()),"hash:2",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        var result=service.apply(service.prepare(100L,"101",List.of(header),List.of(original,duplicate),actor),actor);
        assertEquals(2,result.rows().size());assertEquals(2,stored.size());assertNotEquals(result.rows().get(0).targets().getFirst().targetId(),result.rows().get(1).targets().getFirst().targetId());
    }
    @Test void omittedOriginalColumnWrongProjectAndCrossTenantFailClosed() {
        raw.remove("dataSource");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(IllegalArgumentException.class,this::prepare);
        raw.put("dataSource","CRM");raw.put("projectCode","OTHER");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(IllegalArgumentException.class,this::prepare);
        raw.put("projectCode","SOURCE");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));original.setTenantId(2L);assertThrows(IllegalArgumentException.class,this::prepare);verify(mapper,never()).insert(any(ProjectProductConfigurationDO.class));
    }
    @Test void existingUnboundRowsAndSecondCaptureAreNotAdopted() {
        when(mapper.countAllProjectRecords(any())).thenReturn(1L);assertThrows(IllegalArgumentException.class,this::prepare);
        when(mapper.countAllProjectRecords(any())).thenReturn(0L);var occupied=new ProjectProductConfigurationDO();occupied.setId(999L);stored.put(999L,occupied);assertThrows(IllegalArgumentException.class,this::prepare);verify(mapper,never()).insert(any(ProjectProductConfigurationDO.class));
    }
    @Test void fractionalIntegersAndExcessPrecisionCannotBeTruncated() {
        raw.put("quantity","1.5");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(ArithmeticException.class,this::prepare);
        raw.put("quantity","3");raw.put("itemGroup","2.5");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(ArithmeticException.class,this::prepare);verify(mapper,never()).insert(any(ProjectProductConfigurationDO.class));
    }
    @Test void replayChecksAllFormalFieldsAndAuditWithoutUpdatingThem() {
        service.apply(prepare(),actor);String baseline=ProjectProductConfigurationImportService.baseline(persisted);
        var target=new ExternalTargetMapping("PROJ","ProjectProductConfiguration","proj_project_product_configuration",persisted.getId(),"PROJECT_ENTITY",0);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target),null,null,null,baseline)));
        clearInvocations(mapper);assertEquals(baseline,service.apply(prepare(),actor).baselines().get("hash:1"));verify(mapper,never()).insert(any(ProjectProductConfigurationDO.class));verify(mapper,never()).updateById(any(ProjectProductConfigurationDO.class));
        persisted.setUpdater("manual");assertThrows(IllegalArgumentException.class,this::prepare);persisted.setUpdater("31");persisted.setQuantity(BigDecimal.TEN);assertThrows(IllegalArgumentException.class,this::prepare);
        persisted.setQuantity(new BigDecimal("3"));persisted.setDeleted(true);assertThrows(IllegalArgumentException.class,this::prepare);
    }
    @Test void authorityFailureMissingBaselineAndMismatchedHeaderAreRejected() {
        doThrow(new IllegalArgumentException("FORBIDDEN")).when(access).getProjectForManage(any(),any());assertThrows(IllegalArgumentException.class,this::prepare);verifyNoInteractions(mapper);
        reset(access);header.setSourceKey("102");assertThrows(IllegalArgumentException.class,this::prepare);header.setSourceKey("101");
        var target=new ExternalTargetMapping("PROJ","ProjectProductConfiguration","proj_project_product_configuration",700L,"PROJECT_ENTITY",0);when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target))));assertThrows(IllegalArgumentException.class,this::prepare);
    }
    @Test void emptyPilotHasNoInventedRowsOrMappings() {
        var plan=service.prepare(100L,"101",List.of(header),List.of(),actor);assertTrue(service.apply(plan,actor).rows().isEmpty());verifyNoInteractions(mapper,evidence);
    }

    cn.iocoder.yudao.module.pms.project.api.migration.ProjectAssociatedProductMigrationApi.Source direct(String key,Long id,String baseline) {
        return new cn.iocoder.yudao.module.pms.project.api.migration.ProjectAssociatedProductMigrationApi.Source("a".repeat(64)+":"+key,new LinkedHashMap<>(raw),id,baseline);
    }
    @Test void directSqlRowsUseTheExistingOwnerAndKeepDuplicatesAndReplayProtection() {
        var plan=service.prepareSources(new ProjectProductConfigurationImportService.DirectPreparation(100L,"SOURCE-0",List.of(direct("1",null,null),direct("2",null,null)),actor));
        var preview=service.sourceResults(new ProjectProductConfigurationImportService.SourceExecution(plan,actor,false));assertEquals(2,preview.size());
        assertEquals(19,preview.getFirst().fields().size());assertNull(preview.getFirst().fields().get("parentCode"));
        verify(mapper,never()).insert(any(ProjectProductConfigurationDO.class));verifyNoInteractions(evidence);
        var results=service.sourceResults(new ProjectProductConfigurationImportService.SourceExecution(plan,actor,true));assertEquals(2,stored.size());
        assertNotEquals(results.get(0).targetId(),results.get(1).targetId());
        var replay=java.util.stream.IntStream.range(0,2).mapToObj(i->direct(String.valueOf(i+1),results.get(i).targetId(),results.get(i).baseline())).toList();
        clearInvocations(mapper);
        assertTrue(service.sourceResults(new ProjectProductConfigurationImportService.SourceExecution(service.prepareSources(new ProjectProductConfigurationImportService.DirectPreparation(100L,"SOURCE-0",replay,actor)),actor,true))
                .stream().allMatch(result->"UNCHANGED".equals(result.action())));
        verify(mapper,never()).insert(any(ProjectProductConfigurationDO.class));verify(mapper,never()).updateById(any(ProjectProductConfigurationDO.class));
        persisted.setUpdater("manual");assertThrows(IllegalArgumentException.class,()->service.prepareSources(new ProjectProductConfigurationImportService.DirectPreparation(100L,"SOURCE-0",replay,actor)));
    }
    @Test void directSqlInputRejectsMissingFieldsOrDifferentProjectAndCannotAdoptAnExistingList() {
        var valid=direct("1",null,null);
        assertThrows(IllegalArgumentException.class,()->service.prepareSources(new ProjectProductConfigurationImportService.DirectPreparation(100L,"OTHER-0",List.of(valid),actor)));
        raw.remove("bomPaths");var missing=direct("1",null,null);
        assertThrows(IllegalArgumentException.class,()->service.prepareSources(new ProjectProductConfigurationImportService.DirectPreparation(100L,"SOURCE-0",List.of(missing),actor)));
        when(mapper.countAllProjectRecords(any())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class,()->service.prepareSources(new ProjectProductConfigurationImportService.DirectPreparation(100L,"SOURCE-0",List.of(valid),actor)));
        verify(mapper,never()).insert(any(ProjectProductConfigurationDO.class));verifyNoInteractions(evidence);
    }
}
