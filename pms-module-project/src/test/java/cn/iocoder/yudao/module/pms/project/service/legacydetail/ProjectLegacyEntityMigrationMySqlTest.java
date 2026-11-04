package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetEntityImportApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationAddressImportApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectPartyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectPartyMapper;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailCatalog;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The transaction must roll back a native write when a later Owner fails. Only fresh synthetic rows are used. */
@EnabledIfEnvironmentVariable(named="LEGACY_DETAIL_MYSQL",matches="true")
class ProjectLegacyEntityMigrationMySqlTest {
    private static String fullPayload(String domain,Map<String,String> values) {
        var full=new LinkedHashMap<String,String>();new LegacyDetailCatalog().require(domain).fields().forEach(f->full.put(f.key(),null));
        full.putAll(values);return LegacyDetailNormalizer.sourcePayloadJson(full);
    }
    @Test void laterOwnerFailureRollsBackEarlierNativeEntityWrite() {
        String url=System.getenv("LEGACY_DETAIL_MYSQL_URL");
        assertTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?"));
        var datasource=new DriverManagerDataSource(url,System.getenv("LEGACY_DETAIL_MYSQL_USER"),System.getenv("LEGACY_DETAIL_MYSQL_PASSWORD"));
        var jdbc=new JdbcTemplate(datasource);assertEquals("npdms_domain_test",jdbc.queryForObject("SELECT DATABASE()",String.class));
        long fixtureProject=Long.parseLong(System.getenv("LEGACY_DETAIL_MYSQL_PROJECT_ID"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project WHERE tenant_id=1 AND id=?",Integer.class,fixtureProject));
        String key="TEST-NATIVE-ROLLBACK-"+UUID.randomUUID();
        var mapper=mock(ProjectLegacyDetailMapper.class);var parties=mock(ProjectPartyMapper.class);
        var commerce=mock(DppmsCommerceEntityResolveApi.class);var assets=mock(DppmsAssetEntityImportApi.class);
        var commands=mock(PlatformCommandExecutionApi.class);
        var source=new ProjectLegacySourceDO();source.setId(10L);source.setTenantId(1L);source.setProjectId(fixtureProject);
        source.setSourceProjectKey("101");source.setSourceContractNo("SYNTHETIC");source.setCurrentSnapshotId(20L);
        var snapshot=new ProjectLegacySnapshotDO();snapshot.setId(20L);snapshot.setSourceId(10L);snapshot.setRecordCount(2);
        snapshot.setChecksum("a".repeat(64));snapshot.setSourceReadAt(LocalDateTime.now());
        var counts=new LinkedHashMap<String,Integer>();new LegacyDetailCatalog().domains().forEach(d->counts.put(d.code(),0));
        counts.put("parties",1);counts.put("contracts",1);snapshot.setDomainCountsJson(JsonUtils.toJsonString(counts));
        when(mapper.selectProjectSources(any())).thenReturn(List.of(source));when(mapper.selectSourceForUpdate(any())).thenReturn(source);
        when(mapper.selectSnapshot(any())).thenReturn(snapshot);
        var party=new ProjectLegacyRecordDO();party.setDomainCode("parties");party.setSourceTable("pm_project_related_party");party.setSourceKey(key);
        party.setPayloadJson(fullPayload("parties",Map.of("projectId","101","partyRole","20","partyName","Synthetic party")));
        var contract=new ProjectLegacyRecordDO();contract.setDomainCode("contracts");contract.setSourceKey("301");
        contract.setPayloadJson(fullPayload("contracts",Map.of("contractNo","SYNTHETIC")));
        when(mapper.selectRecords(any())).thenAnswer(invocation->{
            var q=invocation.getArgument(0,ProjectLegacyDetailMapper.RecordsPageQuery.class);
            return q.domainCode().equals("parties")?List.of(party):q.domainCode().equals("contracts")?List.of(contract):List.of();
        });
        doAnswer(invocation->{
            var row=invocation.getArgument(0,ProjectPartyDO.class);
            return jdbc.update("INSERT INTO proj_project_party(id,tenant_id,project_id,party_role,party_name,source_system,source_table,source_record_key,status,version,creator,updater) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    row.getId(),1L,row.getProjectId(),row.getPartyRole(),row.getPartyName(),row.getSourceSystem(),row.getSourceTable(),key,"ACTIVE",0,"TEST","TEST");
        }).when(parties).insert(any(ProjectPartyDO.class));
        when(commands.execute(any(),anyString(),eq(ProjectLegacyEntityMigrationService.Report.class),any(),any()))
                .thenAnswer(invocation -> invocation.getArgument(3,Supplier.class).get());
        when(assets.importEntities(any())).thenThrow(new IllegalStateException("SYNTHETIC_LATER_OWNER_FAILURE"));
        var target=new ProjectLegacyEntityMigrationService(mock(ProjectManualCreationService.class),mapper,new LegacyDetailCatalog(),parties,
                commerce,assets,mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetSourceHistoryApi.class),mock(cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi.class),mock(DppmsInstallationAddressImportApi.class),mock(ProjectLegacyNativeEvidenceService.class),commands,
                mock(ProjectAssociatedDataService.class),mock(cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceDetailImportApi.class), mock(cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationSourceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi.class),
            mock(cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi.class),mock(ProjectFactoryRecordImportService.class));
        var interceptor=new TransactionInterceptor();interceptor.setTransactionManager(new DataSourceTransactionManager(datasource));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(interceptor);
        var service=(ProjectLegacyEntityMigrationService)factory.getProxy();
        TenantContextHolder.setTenantId(1L);
        try {
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_party WHERE tenant_id=1 AND source_record_key=?",Integer.class,key));
            var failure=assertThrows(IllegalStateException.class,()->service.migrate(source.getProjectId(),
                    new ProjectLegacyEntityMigrationService.Request(10L,20L,"a".repeat(64)),new ProjectAccessActor(1L,1L)));
            assertEquals("SYNTHETIC_LATER_OWNER_FAILURE",failure.getMessage());verify(parties).insert(any(ProjectPartyDO.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proj_project_party WHERE tenant_id=1 AND source_record_key=?",Integer.class,key));
        } finally {
            TenantContextHolder.clear();
            jdbc.update("DELETE FROM proj_project_party WHERE tenant_id=1 AND source_system='DPPMS' AND source_table='pm_project_related_party' AND source_record_key=? AND creator='TEST'",key);
        }
    }
}
