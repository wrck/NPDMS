package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectLeaseProductDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectLeaseProductMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProjectLeaseProductImportServiceTest {
    ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    ProjectLeaseProductMapper mapper=mock(ProjectLeaseProductMapper.class);
    ProjectLeaseProductImportService service=new ProjectLeaseProductImportService(access,evidence,mapper);
    ProjectAccessActor actor=new ProjectAccessActor(1L,31L);
    ProjectLegacyRecordDO original,header;ProjectLeaseProductDO persisted;
    Map<String,String> raw;Map<Long,ProjectLeaseProductDO> stored=new LinkedHashMap<>();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);raw=new LinkedHashMap<>();ProjectLeaseProductImportService.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
        raw.putAll(Map.of("projectCode","SOURCE","orderExecNumber","","productFirstName","分类","productName","类型","productSubCode","PRODUCT","productSubModel","MODEL","productSubName","NAME","num","3","leaseDuration","12.50","dataSource","CRM"));
        original=row("lease-products","pm_project_product_lease_line_from_crm","hash:1",raw);
        header=row("project","pm_project_header","101",Map.of("projectId","101","projectCode","SOURCE-0"));
        when(evidence.statuses(eq(1L),eq("101"),eq(20L),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        when(mapper.countAllProjectRecords(any())).thenReturn(0L);
        when(mapper.insert(any(ProjectLeaseProductDO.class))).thenAnswer(call->{persisted=call.getArgument(0);stored.put(persisted.getId(),persisted);return 1;});
        when(mapper.selectRecordForUpdate(any())).thenAnswer(call->stored.get(call.getArgument(0,cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectLeaseProductRecordQuery.class).id()));
        when(mapper.selectBusinessRecordsForUpdate(any())).thenAnswer(call->new ArrayList<>(stored.values()));
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    ProjectLegacyRecordDO row(String domain,String table,String key,Map<String,String> values) {
        var r=new ProjectLegacyRecordDO();r.setId(90L);r.setTenantId(1L);r.setSnapshotId(20L);r.setDomainCode(domain);r.setSourceTable(table);r.setSourceKey(key);r.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(values));return r;
    }
    ProjectLeaseProductImportService.Plan prepare(){return service.prepare(100L,"101",List.of(header),List.of(original),actor);}
    @Test void allBusinessFieldsEmptyStringsNullsAndActualAuditAreCarried() {
        var result=service.apply(prepare(),actor);assertEquals("SOURCE",persisted.getProjectCode());assertEquals("",persisted.getExecutionNo());assertEquals(new BigDecimal("3"),persisted.getQuantity());assertEquals("CRM",persisted.getDataSource());
        assertEquals("分类",persisted.getProductCategory());assertEquals("类型",persisted.getProductType());assertEquals("NAME",persisted.getProductName());assertEquals(new BigDecimal("12.50"),persisted.getLeaseDuration());
        assertEquals("31",persisted.getCreator());assertEquals("31",persisted.getUpdater());assertEquals(persisted.getCreateTime(),persisted.getUpdateTime());assertEquals(0,persisted.getCreateTime().getNano()%1_000_000);
        assertEquals(ProjectLeaseProductImportService.baseline(persisted),result.baselines().get("hash:1"));
    }
    @Test void originalDuplicateRowsReceiveSeparateEntitiesAndBindings() {
        var duplicate=row("lease-products","pm_project_product_lease_line_from_crm","hash:2",raw);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of()),"hash:2",new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        var result=service.apply(service.prepare(100L,"101",List.of(header),List.of(original,duplicate),actor),actor);
        assertEquals(2,result.rows().size());assertEquals(2,stored.size());assertNotEquals(result.rows().get(0).targets().getFirst().targetId(),result.rows().get(1).targets().getFirst().targetId());
    }
    @Test void omittedOriginalColumnWrongProjectAndCrossTenantFailClosed() {
        raw.remove("dataSource");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(IllegalArgumentException.class,this::prepare);
        raw.put("dataSource","CRM");raw.put("projectCode","OTHER");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(IllegalArgumentException.class,this::prepare);
        raw.put("projectCode","SOURCE");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));original.setTenantId(2L);assertThrows(IllegalArgumentException.class,this::prepare);verify(mapper,never()).insert(any(ProjectLeaseProductDO.class));
    }
    @Test void existingUnboundRowsAndSecondCaptureAreNotAdopted() {
        when(mapper.countAllProjectRecords(any())).thenReturn(1L);assertThrows(IllegalArgumentException.class,this::prepare);
        when(mapper.countAllProjectRecords(any())).thenReturn(0L);var occupied=new ProjectLeaseProductDO();occupied.setId(999L);stored.put(999L,occupied);assertThrows(IllegalArgumentException.class,this::prepare);verify(mapper,never()).insert(any(ProjectLeaseProductDO.class));
    }
    @Test void fractionalIntegersAndExcessPrecisionCannotBeTruncated() {
        raw.put("num","1.5");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(ArithmeticException.class,this::prepare);
        raw.put("num","3");raw.put("leaseDuration","1.125");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(ArithmeticException.class,this::prepare);verify(mapper,never()).insert(any(ProjectLeaseProductDO.class));
    }
    @Test void unadjudicatedNonPositiveOrMissingRequiredSourceQuantityCannotBeInvented() {
        for(String quantity:Arrays.asList("0","-1",null)) {
            raw.put("num",quantity);original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));
            assertThrows(IllegalArgumentException.class,this::prepare);
        }
        verify(mapper,never()).insert(any(ProjectLeaseProductDO.class));
    }
    @Test void replayChecksAllFormalFieldsAndAuditWithoutUpdatingThem() {
        service.apply(prepare(),actor);String baseline=ProjectLeaseProductImportService.baseline(persisted);
        var target=new ExternalTargetMapping("PROJ","ProjectLeaseProduct","proj_project_lease_product",persisted.getId(),"PROJECT_ENTITY",0);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target),null,null,null,baseline)));
        clearInvocations(mapper);assertEquals(baseline,service.apply(prepare(),actor).baselines().get("hash:1"));verify(mapper,never()).insert(any(ProjectLeaseProductDO.class));verify(mapper,never()).updateById(any(ProjectLeaseProductDO.class));
        persisted.setUpdater("manual");assertThrows(IllegalArgumentException.class,this::prepare);persisted.setUpdater("31");persisted.setQuantity(BigDecimal.TEN);assertThrows(IllegalArgumentException.class,this::prepare);
        persisted.setQuantity(new BigDecimal("3"));persisted.setDeleted(true);assertThrows(IllegalArgumentException.class,this::prepare);
    }
    @Test void authorityFailureMissingBaselineAndMismatchedHeaderAreRejected() {
        doThrow(new IllegalArgumentException("FORBIDDEN")).when(access).getProjectForManage(any(),any());assertThrows(IllegalArgumentException.class,this::prepare);verifyNoInteractions(mapper);
        reset(access);header.setSourceKey("102");assertThrows(IllegalArgumentException.class,this::prepare);header.setSourceKey("101");
        var target=new ExternalTargetMapping("PROJ","ProjectLeaseProduct","proj_project_lease_product",700L,"PROJECT_ENTITY",0);when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("hash:1",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target))));assertThrows(IllegalArgumentException.class,this::prepare);
    }
    @Test void emptyPilotHasNoInventedRowsOrMappings() {
        var plan=service.prepare(100L,"101",List.of(header),List.of(),actor);assertTrue(service.apply(plan,actor).rows().isEmpty());verifyNoInteractions(mapper,evidence);
    }
}
