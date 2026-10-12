package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.ProjectMasterSyncProofApi;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSourceAttributesServiceTest {
    private final ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    private final ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    private final ProjectHeaderFinalCustomerService finalCustomers=mock(ProjectHeaderFinalCustomerService.class);
    private final ProjectMasterSyncProofApi proof=mock(ProjectMasterSyncProofApi.class);
    private final ProjectLegacyDetailMapper sources=mock(ProjectLegacyDetailMapper.class);
    private final ProjectSourceAttributesService service=new ProjectSourceAttributesService(access,projects,finalCustomers,proof,sources);
    private final ProjectAccessActor actor=new ProjectAccessActor(1L,7L);
    private ProjectMasterDO target;
    private Map<String,String> original;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);target=new ProjectMasterDO();target.setId(100L);target.setTenantId(1L);
        target.setProjectCode("FORMAL");target.setProjectName("正式项目");target.setLifecycleStatus("NORMAL_CLOSED");
        target.setCurrentStage("S6");target.setCustomInfo("{\"value\":[1,{\"empty\":null}]}");
        target.setOrderCreateTime(LocalDateTime.parse("2023-01-02T03:04:05.123"));target.setDisabled(true);
        when(projects.selectById(100L)).thenReturn(target);
        original=new LinkedHashMap<>();ProjectMasterMigrationApi.ORIGINAL_COLUMNS.forEach(k->original.put(k,null));
        original.put("projectId","301");original.put("projectCode","FORMAL");
        when(proof.verify(any())).thenAnswer(call->List.of(new ProjectMasterSyncProofApi.Proof("301",100L,new LinkedHashMap<>(original))));
        when(finalCustomers.readNames(any(),any())).thenReturn(List.of("实际最终用户"));
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    private ProjectLegacyRecordDO record() {
        var row=new ProjectLegacyRecordDO();row.setSourceTable("pm_project_header");row.setSourceKey("301");
        row.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(original));return row;
    }
    @Test void reconciliationRequiresOwnerProofAndNeverWritesMasterFields() {
        var before=cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(target);
        var result=service.importRecord(100L,record(),actor);
        assertEquals("proj_project",result.targets().getFirst().targetTable());
        assertEquals(before,cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(target));
        verify(proof).verify(new ProjectMasterSyncProofApi.Query(1L,7L,List.of("301")));
        verify(projects,never()).updateById(any(ProjectMasterDO.class));
        verify(projects,never()).selectByIdForUpdate(any());
    }
    @Test void finalCustomerUsesItsFormalPartyOwnerAfterProof() {
        var party=new cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping("PROJ","ProjectParty","proj_project_party",200L,"PARTY",0);
        when(finalCustomers.importHeader(any(),any(),any())).thenReturn(party);
        assertEquals(party,service.importRecord(100L,record(),actor).targets().get(1));
    }
    @Test void missingWrongTargetOrChangedOriginalProofCannotUpdatePartyOrMaster() {
        for(var result:List.of(List.<ProjectMasterSyncProofApi.Proof>of(),
                List.of(new ProjectMasterSyncProofApi.Proof("301",101L,original)),
                List.of(new ProjectMasterSyncProofApi.Proof("302",100L,original)))) {
            when(proof.verify(any())).thenReturn(result);
            assertThrows(IllegalStateException.class,()->service.importRecord(100L,record(),actor));
        }
        var changed=new LinkedHashMap<>(original);changed.put("column009","2024-01-02T03:04:05");
        when(proof.verify(any())).thenReturn(List.of(new ProjectMasterSyncProofApi.Proof("301",100L,changed)));
        assertThrows(IllegalStateException.class,()->service.importRecord(100L,record(),actor));
        verifyNoInteractions(finalCustomers);verify(projects,never()).updateById(any(ProjectMasterDO.class));
    }
    @Test void concurrentFormalChangeReportedByOwnerBlocksReconciliation() {
        when(proof.verify(any())).thenThrow(new IllegalStateException("PROJECT_MASTER_CURRENT_FACT_DIFFERS_FROM_SYNC"));
        assertThrows(IllegalStateException.class,()->service.importRecord(100L,record(),actor));
        verifyNoInteractions(finalCustomers);
    }
    @Test void identityTenantDeletedAndScopeFailuresStopBeforeProof() {
        var bad=record();bad.setSourceKey("302");
        assertThrows(IllegalArgumentException.class,()->service.importRecord(100L,bad,actor));
        assertThrows(IllegalArgumentException.class,()->service.readAttributes(100L,new ProjectAccessActor(2L,7L)));
        target.setDeleted(true);assertThrows(IllegalArgumentException.class,()->service.importRecord(100L,record(),actor));
        target.setDeleted(false);target.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->service.readAttributes(100L,actor));
        doThrow(new IllegalArgumentException("DENIED")).when(access).getProjectForManage(100L,actor);
        assertThrows(IllegalArgumentException.class,()->service.importRecord(100L,record(),actor));
        verifyNoInteractions(proof,finalCustomers);
    }
    @Test void readsFormalValuesIncludingNullAuditAndCompleteJson() {
        var values=service.readAttributes(100L,actor);
        assertEquals("FORMAL",values.get("projectCode"));assertEquals("NORMAL_CLOSED",values.get("lifecycleStatus"));
        assertEquals(target.getOrderCreateTime(),values.get("orderCreateTime"));assertEquals(target.getCustomInfo(),values.get("customInfo"));
        assertTrue(values.containsKey("updater"));assertNull(values.get("updater"));assertEquals(true,values.get("disabled"));
        assertEquals(List.of("实际最终用户"),values.get("finalCustomerNames"));
        assertTrue(values.keySet().stream().noneMatch(k->k.startsWith("source")||k.startsWith("erp")));
        verifyNoInteractions(proof);verify(projects,never()).updateById(any(ProjectMasterDO.class));
    }
    @Test void traceIdentityComesFromExistingBindingAndMergedSourcesRemainDistinct() {
        var source=new ProjectLegacySourceDO();source.setSourceProjectKey("301");
        when(sources.selectProjectSources(any())).thenReturn(List.of(source));
        assertEquals("301",service.readSourceKey(100L,actor));
        when(sources.selectProjectSources(any())).thenReturn(List.of(source,source));
        assertNull(service.readSourceKey(100L,actor));
        when(sources.selectProjectSources(any())).thenReturn(List.of());assertNull(service.readSourceKey(100L,actor));
        verify(sources,times(3)).selectProjectSources(argThat(q->q.tenantId()==1L&&q.projectId()==100L));
    }
}
