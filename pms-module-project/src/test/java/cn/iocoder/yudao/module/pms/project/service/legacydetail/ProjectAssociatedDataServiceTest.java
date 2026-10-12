package cn.iocoder.yudao.module.pms.project.service.legacydetail;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.associated.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ProjectAssociatedDataServiceTest {
    ProjectRealProductMapper real=mock(ProjectRealProductMapper.class);
    ProjectLeaseProductMapper lease=mock(ProjectLeaseProductMapper.class);
    ProjectProductConfigurationMapper configuration=mock(ProjectProductConfigurationMapper.class);
    ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    ProjectProductScopeImportService importer=mock(ProjectProductScopeImportService.class);
    cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi scopes=mock(cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.class);
    ProjectLeaseProductImportService leaseOwner=mock(ProjectLeaseProductImportService.class);
    ProjectProductConfigurationImportService configurationOwner=mock(ProjectProductConfigurationImportService.class);
    ProjectSourceAttributesService sourceAttributes=mock(ProjectSourceAttributesService.class);
    ProjectAssociatedDataService service=new ProjectAssociatedDataService(access,mock(ProjectMasterMapper.class),
            mock(DppmsCommerceDetailImportApi.class),sourceAttributes,mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.class),mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi.class),mock(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi.class),importer,scopes,real,mock(ProjectRealProductImportService.class),lease,leaseOwner,configuration,configurationOwner);
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    Map<String,List<ProjectLegacyRecordDO>> records() {
        var row=new ProjectLegacyRecordDO();row.setSourceTable("pm_project_item");row.setSourceKey("301");
        row.setPayloadJson(JsonUtils.toJsonString(Map.of("itemCode","CODE","projectQuantity","12","orderQuantity","14")));
        return Map.of("project",List.of(),"products",List.of(row),"real-products",List.of(),"lease-products",List.of(),"configured-products",List.of());
    }
    @Test void deferredMaintenanceHasNoFormalBusinessQuery() {
        assertThrows(IllegalArgumentException.class,()->service.page(100L,"maintenance",1,20,new ProjectAccessActor(1L,1L)));
        verifyNoInteractions(real,lease,leaseOwner,configuration,configurationOwner);
    }
    @Test void originalProductsAreCarriedByFormalScopeOwnerWithoutAParallelBusinessWrite() {
        var command=new cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.Command(1L,1L,100L,1L,"101",List.of());
        when(importer.command(eq(100L),any(),anyList(),any(),eq(false))).thenReturn(command);
        when(scopes.apply(command)).thenReturn(List.of(new cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.Result("301",400L,"CREATED",Map.of("creator","original","createTime",java.time.LocalDateTime.of(2020,1,2,3,4),"updater","original","updateTime",java.time.LocalDateTime.of(2020,1,2,3,4)))));
        var result=service.importRecords(100L,records(),new ProjectAccessActor(1L,1L));
        assertEquals("com_delivery_scope",result.rows().getFirst().targets().getFirst().targetTable());
        verifyNoInteractions(real,lease,leaseOwner,configuration,configurationOwner);
    }
    @Test void scopeOwnerConflictCannotFallBackToWritingTheOldProductTable() {
        when(importer.command(any(),any(),anyList(),any(),anyBoolean())).thenThrow(new IllegalArgumentException("BASELINE_CHANGED"));
        assertThrows(RuntimeException.class,()->service.importRecords(100L,records(),new ProjectAccessActor(1L,1L)));
        verifyNoInteractions(scopes,real,lease,leaseOwner,configuration,configurationOwner);
    }
    @Test void additionalChildrenUseTheirFormalOwnersAndSeparateReplayBaselines() {
        var data=new HashMap<String,List<ProjectLegacyRecordDO>>(records());data.put("products",List.of());
        var header=new ProjectLegacyRecordDO();header.setSourceKey("101");data.put("project",List.of(header));
        var leaseRow=new ProjectLegacyRecordDO();leaseRow.setSourceKey("LEASE");var configRow=new ProjectLegacyRecordDO();configRow.setSourceKey("CONFIG");
        data.put("lease-products",List.of(leaseRow));data.put("configured-products",List.of(configRow));
        var actor=new ProjectAccessActor(1L,1L);var lp=new ProjectLeaseProductImportService.Plan(100L,List.of());var cp=new ProjectProductConfigurationImportService.Plan(100L,List.of());
        when(leaseOwner.prepare(100L,"101",List.of(header),List.of(leaseRow),actor)).thenReturn(lp);
        when(configurationOwner.prepare(100L,"101",List.of(header),List.of(configRow),actor)).thenReturn(cp);
        when(leaseOwner.apply(lp,actor)).thenReturn(new ProjectLeaseProductImportService.Result(List.of(),Map.of("LEASE","lease-proof")));
        when(configurationOwner.apply(cp,actor)).thenReturn(new ProjectProductConfigurationImportService.Result(List.of(),Map.of("CONFIG","config-proof")));
        when(sourceAttributes.importRecord(100L,header,actor)).thenReturn(cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow.mapped("project","101",new cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping("PROJ","Project","proj_project",100L,"PROJECT_ENTITY",0)));
        var result=service.importRecords(100L,data,actor);assertEquals(Map.of("LEASE","lease-proof"),result.leaseBaselines());assertEquals(Map.of("CONFIG","config-proof"),result.configurationBaselines());
        verifyNoInteractions(real,lease,configuration,scopes);
    }
    @Test void configurationParentsUsePathsAndReportMissingNodesWithoutInventingThem() {
        var present=new ProjectProductConfigurationDO();present.setId(501L);present.setProjectCode("CODE");present.setExecutionNo("");present.setItemGroup(2);present.setBomPath("ROOT,CHILD");present.setLevel(2);
        var orphan=new ProjectProductConfigurationDO();orphan.setId(502L);orphan.setProjectCode("CODE");orphan.setExecutionNo("");orphan.setItemGroup(3);orphan.setBomPath("MISSING,CHILD");orphan.setLevel(2);
        var parent=new ProjectProductConfigurationDO();parent.setProjectCode("CODE");parent.setExecutionNo("");parent.setItemGroup(2);parent.setBomPath("ROOT");
        when(configuration.selectProjectPage(any())).thenReturn(List.of(present,orphan));when(configuration.countProject(any())).thenReturn(2L);
        when(configuration.selectPathNodes(any())).thenReturn(List.of(parent));
        var page=service.page(100L,"configured-products",1,20,new ProjectAccessActor(1L,1L));
        assertEquals(false,page.getList().get(0).values().get("parentPathMissing"));assertEquals(true,page.getList().get(1).values().get("parentPathMissing"));
        assertEquals("ROOT",page.getList().get(0).values().get("parentBomPaths"));assertEquals("2_ROOT",page.getList().get(0).values().get("parentGroupPaths"));
        assertEquals("2_ROOT,CHILD",page.getList().get(0).values().get("groupPaths"));verify(configuration,never()).insert(any(ProjectProductConfigurationDO.class));
        assertEquals("a,b,c,d",ProjectAssociatedDataService.parentBomPath("a,b,c,d,e",5));
    }
}
