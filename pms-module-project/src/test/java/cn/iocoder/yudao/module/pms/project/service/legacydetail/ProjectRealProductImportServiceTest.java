package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.ProjectRealProductDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectRealProductImportServiceTest {
    ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    ProjectRealProductMapper mapper=mock(ProjectRealProductMapper.class);
    ProjectRealProductImportService service=new ProjectRealProductImportService(access,evidence,mapper);
    ProjectAccessActor actor=new ProjectAccessActor(1L,31L);
    ProjectLegacyRecordDO original,upstream;
    ProjectRealProductDO persisted;
    Map<String,String> raw;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        raw=new LinkedHashMap<>();ProjectRealProductImportService.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
        raw.putAll(Map.of("id","301","projectId","101","contractNo","","projectCode","COMMERCIAL-CODE","orderExecNumber","X1-V1",
                "productSubCode","PRODUCT","productSubModel","MODEL","productSubName","NAME","num","-2"));
        original=row("real-products","pm_project_product_line_real","301",raw);
        var values=new LinkedHashMap<>(raw);values.remove("id");values.remove("projectId");values.remove("contractNo");values.put("dataSource","SMS");
        upstream=row("real-product-upstream","pm_project_real_product_line_from_sms","sha:1",values);
        when(evidence.statuses(eq(1L),eq("101"),eq(20L),anyList())).thenReturn(Map.of("301",
                new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of())));
        when(mapper.insert(any(ProjectRealProductDO.class))).thenAnswer(call->{persisted=call.getArgument(0);return 1;});
        when(mapper.selectRecordForUpdate(any())).thenAnswer(call->persisted);
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    ProjectLegacyRecordDO row(String domain,String table,String key,Map<String,String> values) {
        var r=new ProjectLegacyRecordDO();r.setId(90L);r.setTenantId(1L);r.setSnapshotId(20L);r.setDomainCode(domain);r.setSourceTable(table);r.setSourceKey(key);
        r.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(values));return r;
    }
    ProjectRealProductImportService.Plan prepare(){return service.prepare(100L,"101",List.of(original),List.of(upstream),actor);}
    @Test void actualCommercialCodeEmptyNullAndNegativeQuantityAreCarriedWithoutAllocation() {
        var plan=prepare();var result=service.apply(plan,actor);
        assertEquals("COMMERCIAL-CODE",persisted.getProjectCode());assertEquals("",persisted.getContractNo());assertNull(persisted.getMemo());
        assertNull(persisted.getProductName());assertEquals(new BigDecimal("-2"),persisted.getQuantity());assertEquals("SMS",persisted.getDataSource());
        assertEquals("31",persisted.getCreator());assertEquals("31",persisted.getUpdater());assertNotNull(persisted.getCreateTime());
        assertEquals(persisted.getCreateTime(),persisted.getUpdateTime());assertEquals(0,persisted.getCreateTime().getNano()%1_000_000);
        assertEquals(ProjectRealProductImportService.baseline(persisted),result.baselines().get("301"));
        assertEquals(persisted.getId(),result.rows().getFirst().targets().getFirst().targetId());
        var provenance=ProjectRealProductReconciliationService.upstreamOutcomes(plan,List.of(upstream),result);
        assertEquals(persisted.getId(),provenance.getFirst().targets().getFirst().targetId());
    }
    @Test void zeroAndNullQuantityAreNotReplacedByOrderQuantity() {
        for(String quantity:Arrays.asList("0",null)) {
            raw.put("num",quantity);original=row("real-products","pm_project_product_line_real","301",raw);
            var values=new LinkedHashMap<>(raw);values.keySet().retainAll(ProjectRealProductImportService.SHARED_COLUMNS);values.put("dataSource","SMS");
            upstream=row("real-product-upstream","pm_project_real_product_line_from_sms","sha:1",values);
            persisted=null;var plan=prepare();service.apply(plan,actor);
            assertEquals(quantity==null?null:new BigDecimal(quantity),persisted.getQuantity());
        }
    }
    @Test void missingOrAmbiguousUpstreamDoesNotReadBindingOrWriteBusinessRow() {
        assertThrows(IllegalArgumentException.class,()->service.prepare(100L,"101",List.of(original),List.of(),actor));
        var duplicate=row("real-product-upstream","pm_project_real_product_line_from_sms","sha:2",
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(upstream.getPayloadJson(),new tools.jackson.core.type.TypeReference<Map<String,String>>(){}));
        assertThrows(IllegalArgumentException.class,()->service.prepare(100L,"101",List.of(original),List.of(upstream,duplicate),actor));
        verify(mapper,never()).insert(any(ProjectRealProductDO.class));
    }
    @Test void omittedOriginalNullColumnAndCrossProjectOrTenantAreRejected() {
        raw.remove("memo");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(IllegalArgumentException.class,this::prepare);
        raw.put("memo",null);raw.put("projectId","102");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));assertThrows(IllegalArgumentException.class,this::prepare);
        raw.put("projectId","101");original.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(raw));upstream.setTenantId(2L);assertThrows(IllegalArgumentException.class,this::prepare);
        verify(mapper,never()).insert(any(ProjectRealProductDO.class));
    }
    @Test void existingUnboundRecordIsBlockedRatherThanDuplicatedOrAdoptedAcrossRefreshes() {
        when(mapper.selectBusinessRecordsForUpdate(any())).thenReturn(List.of(new ProjectRealProductDO()));
        assertThrows(IllegalArgumentException.class,this::prepare);verify(mapper,never()).insert(any(ProjectRealProductDO.class));
    }
    @Test void rebuiltIdsAndChangedQuantitiesCannotStartAnUnadjudicatedSecondCapture() {
        when(mapper.countAllProjectRecords(any())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class,this::prepare);verify(mapper,never()).insert(any(ProjectRealProductDO.class));
    }
    @Test void replayProtectsAllPersistedBusinessAndAuditFields() {
        service.apply(prepare(),actor);String baseline=ProjectRealProductImportService.baseline(persisted);
        var target=new ExternalTargetMapping("PROJ","ProjectRealProduct","proj_project_real_product",persisted.getId(),"PROJECT_ENTITY",0);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("301",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target),null,null,baseline)));
        clearInvocations(mapper);var unchanged=service.apply(prepare(),actor);assertEquals(baseline,unchanged.baselines().get("301"));verify(mapper,never()).insert(any(ProjectRealProductDO.class));
        persisted.setUpdater("manual-editor");assertThrows(IllegalArgumentException.class,this::prepare);
        persisted.setUpdater("31");persisted.setQuantity(BigDecimal.ONE);assertThrows(IllegalArgumentException.class,this::prepare);
        persisted.setQuantity(new BigDecimal("-2"));persisted.setDeleted(true);assertThrows(IllegalArgumentException.class,this::prepare);
    }
    @Test void explicitAuthorityFailureAndMissingBindingBaselineAreNeverBypassed() {
        doThrow(new IllegalArgumentException("FORBIDDEN")).when(access).getProjectForManage(any(),any());assertThrows(IllegalArgumentException.class,this::prepare);verifyNoInteractions(mapper);
        reset(access);var target=new ExternalTargetMapping("PROJ","ProjectRealProduct","proj_project_real_product",700L,"PROJECT_ENTITY",0);
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(Map.of("301",new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,List.of(target))));
        assertThrows(IllegalArgumentException.class,this::prepare);verifyNoInteractions(mapper);
    }
    @Test void emptyApprovedProjectHasNoInventedRecordsOrSourceMappings() {
        var plan=service.prepare(100L,"101",List.of(),List.of(),actor);assertTrue(service.apply(plan,actor).rows().isEmpty());verifyNoInteractions(mapper,evidence);
    }
}
