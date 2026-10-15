package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.*;
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
@EnabledIfSystemProperty(named="npdms.associated-list.mysql",matches="true")
class ProjectLeaseProductOwnerMySqlTest {
    Connection db;org.apache.ibatis.session.SqlSession session;ProjectLeaseProductMapper mapper;
    ProjectLegacyNativeEvidenceService evidence=mock(ProjectLegacyNativeEvidenceService.class);
    ProjectLeaseProductImportService owner;ProjectAccessActor actor=new ProjectAccessActor(1L,31L);
    List<ProjectLegacyRecordDO> originals=new ArrayList<>(),headers=new ArrayList<>();
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        Path root=Path.of(System.getProperty("user.dir"));if(root.getFileName().toString().equals("pms-module-project"))root=root.getParent();
        String migration=Files.readString(root.resolve("sql/migrations/V412__dppms_native_detail_field_completion.sql"));
        int start=migration.indexOf("CREATE TABLE proj_project_lease_product");
        String ddl=migration.substring(start,migration.indexOf(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;",start)+") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;".length());
        try(var sql=db.createStatement()){sql.execute(ddl.replace("CREATE TABLE proj_project_lease_product","CREATE TEMPORARY TABLE proj_project_lease_product"));}
        db.setAutoCommit(false);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD")));
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
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/associated/ProjectLeaseProductMapper.xml"));
        session=factory.getObject().openSession(db);mapper=session.getMapper(ProjectLeaseProductMapper.class);TenantContextHolder.setTenantId(1L);
        owner=new ProjectLeaseProductImportService(mock(ProjectManualCreationService.class),evidence,mapper);
        Map<String,ProjectLegacyNativeEvidenceService.RowStatus> statuses=new LinkedHashMap<>();
        headers.add(row("project","pm_project_header","101",Map.of("projectId","101","projectCode","BusinessCode-0")));
        for(int i=0;i<3;i++) {
            String key="hash:"+(i+1);var raw=new LinkedHashMap<String,String>();ProjectLeaseProductImportService.ORIGINAL_COLUMNS.forEach(k->raw.put(k,null));
            raw.putAll(Map.of("projectCode","BusinessCode","orderExecNumber","","productFirstName","分类","productName","类型","productSubCode",i<2?"SKU":"OTHER","productSubModel","MODEL","productSubName","Name","leaseDuration","12.50","dataSource","CRM"));raw.put("num",i<2?"3":"4");
            originals.add(row("lease-products","pm_project_product_lease_line_from_crm",key,raw));
            statuses.put(key,new ProjectLegacyNativeEvidenceService.RowStatus("NOT_MIGRATED",null,null,List.of()));
        }
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(statuses);
    }
    ProjectLegacyRecordDO row(String domain,String table,String key,Map<String,String> values) {
        var row=new ProjectLegacyRecordDO();row.setId(90L);row.setTenantId(1L);row.setSnapshotId(20L);row.setDomainCode(domain);row.setSourceTable(table);row.setSourceKey(key);
        row.setPayloadJson(LegacyDetailNormalizer.sourcePayloadJson(values));return row;
    }
    ProjectLeaseProductImportService.Plan prepare(){return owner.prepare(100L,"101",headers,originals,actor);}
    @AfterEach void cleanup() throws Exception {TenantContextHolder.clear();if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void actualOwnerPersistsDuplicateRowsAllValuesAndAuditPrecision() {
        var result=owner.apply(prepare(),actor);assertEquals(3,result.rows().size());session.commit();
        var rows=mapper.selectProjectPage(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(1L,"BusinessCode",0,20));assertEquals(3,rows.size());
        assertEquals(List.of("3.000000","3.000000","4.000000"),rows.stream().map(row->row.getQuantity().toPlainString()).sorted().toList());
        for(var row:rows) {assertEquals("BusinessCode",row.getProjectCode());assertEquals("",row.getExecutionNo());
            assertNull(row.getMemo());assertEquals("分类",row.getProductCategory());assertEquals("类型",row.getProductType());assertEquals(new java.math.BigDecimal("12.50"),row.getLeaseDuration());assertEquals("CRM",row.getDataSource());assertEquals("31",row.getCreator());assertEquals("31",row.getUpdater());
            assertEquals(row.getCreateTime(),row.getUpdateTime());assertEquals(0,row.getCreateTime().getNano()%1_000_000);}
    }
    @Test void actualSqlKeepsTenantProjectDeletedAndCaseSensitiveBusinessIdentityBoundaries() throws Exception {
        owner.apply(prepare(),actor);session.commit();
        var row=mapper.selectProjectPage(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(1L,"BusinessCode",0,20)).getFirst();
        assertNull(mapper.selectRecordForUpdate(new ProjectLeaseProductRecordQuery(2L,100L,row.getId())));
        assertNull(mapper.selectRecordForUpdate(new ProjectLeaseProductRecordQuery(1L,101L,row.getId())));
        assertFalse(mapper.selectBusinessRecordsForUpdate(ProjectLeaseProductImportService.businessQuery(1L,100L,row)).isEmpty());
        row.setProjectCode("businesscode");assertTrue(mapper.selectBusinessRecordsForUpdate(ProjectLeaseProductImportService.businessQuery(1L,100L,row)).isEmpty());
        assertEquals(0,mapper.countProject(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(2L,"BusinessCode",0,20)));
        try(var sql=db.createStatement()){sql.executeUpdate("UPDATE proj_project_lease_product SET deleted=1");}session.clearCache();
        assertEquals(0,mapper.countProject(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(1L,"BusinessCode",0,20)));assertTrue(mapper.selectProjectPage(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(1L,"BusinessCode",0,20)).isEmpty());
        assertTrue(mapper.selectRecordForUpdate(new ProjectLeaseProductRecordQuery(1L,100L,row.getId())).getDeleted());
    }
    @Test void successfulReplayDoesNotUpdateAndManualAuditChangeBlocksThroughActualMapper() throws Exception {
        var result=owner.apply(prepare(),actor);session.commit();
        var statuses=new LinkedHashMap<String,ProjectLegacyNativeEvidenceService.RowStatus>();
        result.rows().forEach(r->statuses.put(r.sourceKey(),new ProjectLegacyNativeEvidenceService.RowStatus("MAPPED",null,null,r.targets(),null,null,null,result.baselines().get(r.sourceKey()))));
        when(evidence.statuses(any(),any(),any(),anyList())).thenReturn(statuses);
        assertEquals(result.baselines(),owner.apply(prepare(),actor).baselines());session.commit();
        try(var sql=db.createStatement()){sql.executeUpdate("UPDATE proj_project_lease_product SET updater='manual'");}session.clearCache();
        assertThrows(IllegalArgumentException.class,this::prepare);
    }
    @Test void failedTransactionLeavesNoBusinessRowsOrAuditAssignments() throws Exception {
        owner.apply(prepare(),actor);session.rollback();session.clearCache();
        assertEquals(0,mapper.countProject(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(1L,"BusinessCode",0,20)));
    }
    @Test void anotherCaptureWithChangedQuantityIsBlockedUntilIdentityRulesAreAdjudicated() {
        owner.apply(prepare(),actor);session.commit();
        // A new snapshot has no mappings; modifying quantity does not make it a new business list.
        originals.getFirst().setPayloadJson(originals.getFirst().getPayloadJson().replace("\"num\":\"3\"","\"num\":\"9\""));
        originals.subList(1,originals.size()).clear();
        assertThrows(IllegalArgumentException.class,this::prepare);
        assertEquals(3,mapper.countProject(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(1L,"BusinessCode",0,20)));
    }

    @Test void nativeDisplayUsesTheOriginalLatestExecutionRuleWithoutDeletingEarlierFacts() throws Exception {
        for(int i=0;i<originals.size();i++)originals.get(i).setPayloadJson(originals.get(i).getPayloadJson()
                .replace("\"orderExecNumber\":\"\"","\"orderExecNumber\":\""+(i==0?"A001":"A002")+"\""));
        owner.apply(prepare(),actor);session.commit();
        var query=new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(1L,"BusinessCode",0,20);var page=mapper.selectProjectPage(query);
        assertEquals(2,page.size());assertEquals(2L,mapper.countProject(query));assertTrue(page.stream().allMatch(row->"A002".equals(row.getExecutionNo())));
        assertEquals(3L,mapper.countAllProjectRecords(new ProjectLeaseProductProjectQuery(1L,100L)));
        assertTrue(mapper.selectProjectPage(new cn.iocoder.yudao.module.pms.project.dal.mysql.associated.query.ProjectDisplayCachePageQuery(2L,"BusinessCode",0,20)).isEmpty());
        try(var sql=db.createStatement()){sql.executeUpdate("UPDATE proj_project_lease_product SET deleted=1 WHERE execution_no='A002'");}session.clearCache();
        var earlier=mapper.selectProjectPage(query);assertEquals(1,earlier.size());assertEquals("A001",earlier.getFirst().getExecutionNo());
        assertEquals(3L,mapper.countAllProjectRecords(new ProjectLeaseProductProjectQuery(1L,100L)));
    }
}
