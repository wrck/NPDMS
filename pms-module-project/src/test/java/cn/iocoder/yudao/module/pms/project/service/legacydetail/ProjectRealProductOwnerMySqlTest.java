package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.ProjectRealProductMapper.*;
import cn.iocoder.yudao.module.pms.project.domain.legacydetail.LegacyDetailNormalizer;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Executes the Owner and actual XML against a connection-local table; permanent tables receive no test rows. */
@EnabledIfSystemProperty(named="npdms.real-product.mysql",matches="true")
class ProjectRealProductOwnerMySqlTest {
    Connection db;org.apache.ibatis.session.SqlSession session;ProjectRealProductMapper mapper;
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    ProjectRealProductImportService owner;ProjectAccessActor actor=new ProjectAccessActor(1L,31L);
    List<ProjectLegacyRecordDO> originals=new ArrayList<>(),upstream=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        Path root=Path.of(System.getProperty("user.dir"));if(root.getFileName().toString().equals("pms-module-project"))root=root.getParent();
        String migration=Files.readString(root.resolve("sql/migrations/V412__dppms_native_detail_field_completion.sql"));
        String ddl=migration.substring(migration.indexOf("CREATE TABLE proj_project_real_product"),migration.indexOf("CREATE TABLE proj_project_lease_product"));
        try(var sql=db.createStatement()){sql.execute(ddl.replace("CREATE TABLE proj_project_real_product","CREATE TEMPORARY TABLE proj_project_real_product"));}
        db.setAutoCommit(false);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                    @Override public net.sf.jsqlparser.expression.Expression getTenantId() {
                        return new net.sf.jsqlparser.expression.LongValue(1);
                    }
                }));
        factory.setPlugins(plugins);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/associated/ProjectRealProductMapper.xml"));
        session=factory.getObject().openSession(db);mapper=session.getMapper(ProjectRealProductMapper.class);TenantContextHolder.setTenantId(1L);
        owner=new ProjectRealProductImportService(mock(ProjectManualCreationService.class),evidence,mapper);
        Map<String,ProjectLegacyNativeEvidenceService.RowStatus> statuses=new LinkedHashMap<>();
        String[] quantities={"0","-2",null};
        for(int i=0;i<3;i++) {
            String key=Integer.toString(301+i);var raw=new LinkedHashMap<String,String>();ProjectRealProductImportService.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
            raw.putAll(Map.of("id",key,"projectId","101","contractNo","","projectCode","Business-Code","orderExecNumber","X1-V1",
                    "productSubCode","SKU"+i,"productSubModel","MODEL","productSubName","Name","productName",""));raw.put("num",quantities[i]);
            originals.add(row("real-products","pm_project_product_line_real",key,raw));
            var shared=new LinkedHashMap<>(raw);shared.keySet().retainAll(ProjectRealProductImportService.SHARED_COLUMNS);shared.put("dataSource","SMS");
            upstream.add(row("real-product-upstream","pm_project_real_product_line_from_sms","sha"+i+":1",shared));
            statuses.put(key,new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of()));
        }
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(statuses);
    }
    ProjectLegacyRecordDO row(String domain,String table,String key,Map<String,String> values) {
        var row=new ProjectLegacyRecordDO();row.setId(90L);row.setTenantId(1L);row.setSnapshotId(20L);row.setDomainCode(domain);row.setSourceTable(table);row.setSourceKey(key);
        row.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(values));return row;
    }
    ProjectRealProductImportService.Plan prepare(){return owner.prepare(100L,"101",originals,upstream,actor);}
    @AfterEach void cleanup() throws Exception {TenantContextHolder.clear();if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void actualOwnerPersistsAllValuesAndAuditPrecisionWithZeroNegativeAndNullQuantities() {
        var result=owner.apply(prepare(),actor);assertEquals(3,result.rows().size());session.commit();
        var rows=mapper.selectProjectPage(new AssociatedPageQuery(1L,100L,0,20));assertEquals(3,rows.size());
        assertEquals(List.of("-2.000000","0.000000"),rows.stream().filter(r->r.getQuantity()!=null).map(r->r.getQuantity().toPlainString()).sorted().toList());
        assertEquals(1,rows.stream().filter(r->r.getQuantity()==null).count());
        for(var row:rows) {assertEquals("Business-Code",row.getProjectCode());assertEquals("",row.getContractNo());assertEquals("",row.getProductName());
            assertNull(row.getMemo());assertEquals("SMS",row.getDataSource());assertEquals("31",row.getCreator());assertEquals("31",row.getUpdater());
            assertEquals(row.getCreateTime(),row.getUpdateTime());assertEquals(0,row.getCreateTime().getNano()%1_000_000);}
    }
    @Test void actualSqlKeepsTenantProjectDeletedAndCaseSensitiveBusinessIdentityBoundaries() throws Exception {
        owner.apply(prepare(),actor);session.commit();
        var row=mapper.selectProjectPage(new AssociatedPageQuery(1L,100L,0,20)).getFirst();
        assertNull(mapper.selectRecordForUpdate(new RecordIdentityQuery(2L,100L,row.getId())));
        assertNull(mapper.selectRecordForUpdate(new RecordIdentityQuery(1L,101L,row.getId())));
        assertEquals(1,mapper.selectBusinessRecordsForUpdate(ProjectRealProductImportService.businessQuery(1L,100L,row)).size());
        row.setProjectCode("business-code");assertTrue(mapper.selectBusinessRecordsForUpdate(ProjectRealProductImportService.businessQuery(1L,100L,row)).isEmpty());
        assertEquals(0,mapper.countProject(new AssociatedPageQuery(2L,100L,0,20)));
        try(var sql=db.createStatement()){sql.executeUpdate("UPDATE proj_project_real_product SET deleted=1");}session.clearCache();
        assertEquals(0,mapper.countProject(new AssociatedPageQuery(1L,100L,0,20)));assertTrue(mapper.selectProjectPage(new AssociatedPageQuery(1L,100L,0,20)).isEmpty());
        assertTrue(mapper.selectRecordForUpdate(new RecordIdentityQuery(1L,100L,row.getId())).getDeleted());
    }
    @Test void successfulReplayDoesNotUpdateAndManualAuditChangeBlocksThroughActualMapper() throws Exception {
        var result=owner.apply(prepare(),actor);session.commit();
        var statuses=new LinkedHashMap<String,ProjectLegacyNativeEvidenceService.RowStatus>();
        result.rows().forEach(r->statuses.put(r.sourceKey(),new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,r.targets(),null,null,result.baselines().get(r.sourceKey()))));
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(statuses);
        assertEquals(result.baselines(),owner.apply(prepare(),actor).baselines());session.commit();
        try(var sql=db.createStatement()){sql.executeUpdate("UPDATE proj_project_real_product SET updater='manual'");}session.clearCache();
        assertThrows(IllegalArgumentException.class,this::prepare);
    }
    @Test void failedTransactionLeavesNoBusinessRowsOrAuditAssignments() throws Exception {
        owner.apply(prepare(),actor);session.rollback();session.clearCache();
        assertEquals(0,mapper.countProject(new AssociatedPageQuery(1L,100L,0,20)));
    }
    @Test void anotherCaptureWithChangedQuantityIsBlockedUntilIdentityRulesAreAdjudicated() {
        owner.apply(prepare(),actor);session.commit();
        // A new snapshot has no mappings; modifying quantity does not make it a new business list.
        originals.getFirst().setPayloadJson(originals.getFirst().getPayloadJson().replace("\"num\":\"0\"","\"num\":\"3\""));
        upstream.getFirst().setPayloadJson(upstream.getFirst().getPayloadJson().replace("\"num\":\"0\"","\"num\":\"3\""));
        originals.subList(1,originals.size()).clear();upstream.subList(1,upstream.size()).clear();
        assertThrows(IllegalArgumentException.class,this::prepare);
        assertEquals(3,mapper.countProject(new AssociatedPageQuery(1L,100L,0,20)));
    }
}
