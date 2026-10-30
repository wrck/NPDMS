package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class P01CompanyCompletionTest {
    static final LocalDateTime AT=LocalDateTime.of(2026,7,12,0,0);
    static final AbstractSyncTask.SourceQuery<Void> QUERY=new AbstractSyncTask.SourceQuery<>(1001,1,true,null);

    static class Fixture implements AutoCloseable {
        final SqlSession session;
        final P01OrganizationSyncTask.SourceMapper source;
        Fixture() throws Exception {
            var dataSource=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
            var config=new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
            config.setMapUnderscoreToCamelCase(true);
            for(var resource:List.of("base-sync-source/P01SourceMapper.xml","mapper/basesync/P01TargetMapper.xml"))
                try(var input=new ClassPathResource(resource).getInputStream()){
                    new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
                }
            session=new SqlSessionFactoryBuilder().build(config).openSession();
            source=session.getMapper(P01OrganizationSyncTask.SourceMapper.class);
            sql("CREATE DOMAIN UNSIGNED AS BIGINT",
                "CREATE TABLE ehr_company(compID BIGINT PRIMARY KEY,compCode VARCHAR(64),compName VARCHAR(128),isDisabled INT)",
                "CREATE TABLE t_company(id BIGINT PRIMARY KEY,compCode VARCHAR(64),compName VARCHAR(128),state INT)",
                "CREATE TABLE fnd_company(id BIGINT PRIMARY KEY,code VARCHAR(64),name VARCHAR(128),status INT)",
                "CREATE TABLE ehr_department(depID BIGINT PRIMARY KEY,depCode VARCHAR(64),depName VARCHAR(128),compID BIGINT,adminID BIGINT,depGrade VARCHAR(8),director BIGINT,isDisabled INT,remark VARCHAR(128))",
                "CREATE TABLE fnd_department(id BIGINT PRIMARY KEY,departmentNum VARCHAR(64),departmentName VARCHAR(128),status INT,createTime TIMESTAMP,updateTime TIMESTAMP)",
                "CREATE TABLE system_company(id BIGINT PRIMARY KEY,tenant_id BIGINT,code VARCHAR(64),name VARCHAR(128),status INT,version INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN)",
                "CREATE TABLE system_dept(id BIGINT PRIMARY KEY,tenant_id BIGINT,code VARCHAR(64),name VARCHAR(128),status INT,version INT,creator VARCHAR(64),create_time TIMESTAMP,updater VARCHAR(64),update_time TIMESTAMP,deleted BOOLEAN,parent_id BIGINT,sort INT,leader_user_id BIGINT,phone VARCHAR(64),email VARCHAR(128))",
                "INSERT INTO ehr_company VALUES(1,'001','公司一',0),(4,'002','公司二',0),(3,'003','公司三',0)",
                "INSERT INTO ehr_department VALUES(100,'160000','基础部门',4,NULL,'1',NULL,0,NULL)");
        }
        void sql(String... statements) throws Exception {
            try(var statement=session.getConnection().createStatement()){for(var sql:statements)statement.execute(sql);}
            session.clearCache();
        }
        SyncContext<Void> context(boolean preview,int maxRows,SyncIdentityStore identities) {
            return new SyncContext<>(1,2,3,preview,2,maxRows,AT,SyncSource.local(session),identities,
                    mock(SyncReferences.class),"BASE_P01",session,false,true);
        }
        List<P01OrganizationSyncTask.Source> rows() {
            var task=new P01OrganizationSyncTask(session.getMapper(P01OrganizationSyncTask.TargetMapper.class));
            var context=context(true,1000,mock(SyncIdentityStore.class));
            return task.syncDataQuery(context.forParameters(task.createParameters(context)));
        }
        P01OrganizationSyncTask.Source row(String code) {
            return rows().stream().filter(r->code.equals(r.getCode())).findFirst().orElseThrow();
        }
        @Override public void close() throws Exception {
            try { sql("DROP ALL OBJECTS"); } finally { session.close(); }
        }
    }

    @Test void approvedFnd004CompletesCompaniesWithoutChangingEhrDepartmentCompanyReferences() throws Exception {
        try(var f=new Fixture()){
            f.sql("INSERT INTO fnd_company VALUES(4,'04','香港公司',1),(7,'007','范围外公司',1)");
            var rows=f.rows();
            assertEquals(4,rows.stream().filter(r->"COMPANY".equals(r.getKind())).count());
            assertEquals("香港公司",f.row("004").getName());
            assertEquals("fnd_company:4",f.row("004").getSourceId());
            assertEquals(0,f.row("004").getStatus());
            assertEquals("002",f.row("160000").getCompanyCode());
            assertFalse(rows.stream().anyMatch(r->"007".equals(r.getCode())));
        }
    }

    @Test void wholeEhrDefinitionWinsAndTCompanyCompletesMissingCompany() throws Exception {
        try(var f=new Fixture()){
            f.sql("INSERT INTO t_company VALUES(4,'04','T公司四',1),(2,'02','T公司二',0)",
                  "INSERT INTO fnd_company VALUES(4,'04','FND公司四',0)");
            assertEquals("T公司四",f.row("004").getName());
            assertEquals("t_company:4",f.row("004").getSourceId());
            assertEquals("公司二",f.row("002").getName());
            assertEquals(0,f.row("002").getStatus());
            assertEquals("002",f.row("160000").getCompanyCode());
            f.sql("INSERT INTO ehr_company VALUES(99,'004','EHR公司四',0)");
            assertEquals("EHR公司四",f.row("004").getName());
            assertEquals("99",f.row("004").getSourceId());
        }
    }

    @Test void absentSupplementarySnapshotDoesNotBlockDirectoryOrExpandScope() throws Exception {
        try(var f=new Fixture()){
            f.sql("INSERT INTO ehr_department VALUES(101,'888888','范围外',4,100,'3',NULL,0,NULL)",
                  "INSERT INTO fnd_department VALUES(9,'777777','范围外FND',1,'2020-01-01',NULL)");
            assertEquals(4,f.rows().size());
        }
    }

    @Test void sourceQueryResolvesHistoryAndParentsWithoutTechnicalIdAssumptions() throws Exception {
        try(var f=new Fixture()){
            f.sql("INSERT INTO ehr_department VALUES(9999,'160700002','停用部门',4,100,'3',NULL,1,NULL)",
                  "INSERT INTO ehr_department VALUES(200,'167010','EHR名称',4,100,'3',293,0,'后来补充的原备注')",
                  "INSERT INTO fnd_department VALUES(10,'160700002','FND部门',1,'2020-01-01','2020-02-01'),(11,'167010','历史部门',1,'2019-01-01',NULL)");
            var disabled=f.row("160700002");
            assertEquals(1,disabled.getStatus());
            assertEquals("160000",disabled.getParentCode());
            var historical=f.row("167010");
            assertEquals("历史部门",historical.getName());
            assertEquals("160000",historical.getParentCode());
            assertEquals("002",historical.getCompanyCode());
            assertEquals(LocalDateTime.of(2019,1,1,0,0),historical.getCreateTime());
            assertTrue(historical.isHistorical());
            assertEquals("P01_DIRECTOR_ASSOCIATION_PENDING:167010",historical.getWarning());
        }
    }

    @Test void unresolvedSourceReferencesAndConflictingDefinitionsStillFailClosed() throws Exception {
        try(var f=new Fixture()){
            f.sql("INSERT INTO ehr_department VALUES(201,'123456','缺公司',999,100,'2',NULL,0,NULL)");
            assertEquals("P01_RAW_COMPANY_REFERENCE_MISSING",assertThrows(IllegalArgumentException.class,f::rows).getMessage());
            f.sql("DELETE FROM ehr_department WHERE depID=201",
                  "INSERT INTO ehr_company VALUES(9,'001','另一同码来源',0)");
            assertEquals("P01_WHOLE_DEFINITION_SAME_PRIORITY_AMBIGUOUS",assertThrows(IllegalArgumentException.class,f::rows).getMessage());
        }
    }

    @Test void runtimeCapacityControlsProjectionInsteadOfFixedSnapshotSize() throws Exception {
        try(var f=new Fixture()){
            var task=new P01OrganizationSyncTask(f.session.getMapper(P01OrganizationSyncTask.TargetMapper.class));
            var context=f.context(true,3,mock(SyncIdentityStore.class));
            assertEquals("P01_RAW_DIRECTORY_CAPACITY",assertThrows(IllegalArgumentException.class,()->task.createParameters(context)).getMessage());
        }
    }

    @Test void ambiguousDepartmentSourceJoinCannotSilentlyPickOneDefinition() throws Exception {
        try(var f=new Fixture()){
            f.sql("INSERT INTO ehr_department VALUES(900,'160700002','原部门一',4,100,'3',NULL,1,NULL),(901,'160700002','原部门二',4,100,'3',NULL,0,NULL)",
                  "INSERT INTO fnd_department VALUES(10,'160700002','FND部门',1,'2020-01-01',NULL)");
            assertEquals("P01_WHOLE_DEFINITION_SAME_PRIORITY_AMBIGUOUS",assertThrows(IllegalArgumentException.class,f::rows).getMessage());
        }
    }

    @Test void sourcePriorityPreservesWholeEhrDepartmentAndInvalidAliasesAreRejected() throws Exception {
        try(var f=new Fixture()){
            f.sql("INSERT INTO ehr_department VALUES(201,'163130','EHR正式定义',4,100,'2',NULL,0,NULL)",
                  "INSERT INTO fnd_department VALUES(11,'163130','FND旧定义',0,'2020-01-01',NULL)");
            var row=f.row("163130");
            assertEquals("EHR正式定义",row.getName());assertEquals("201",row.getSourceId());
            assertEquals(0,row.getStatus());assertFalse(row.isHistorical());assertNull(row.getCreateTime());
            f.sql("INSERT INTO t_company VALUES(7,'07','无映射公司',1)");
            assertEquals("P01_COMPANY_ALIAS_UNMAPPED",assertThrows(IllegalArgumentException.class,f::rows).getMessage());
        }
    }

    @Test void previewBatchWriteReplayAndHistoryFingerprintProtectionRemainEffective() throws Exception {
        try(var f=new Fixture()){
            var stored=new HashMap<String,SyncIdentityStore.Identity>();
            var identities=mock(SyncIdentityStore.class);
            when(identities.findByKeys(any())).thenAnswer(call->{
                var query=call.<SyncIdentityStore.Query>getArgument(0);
                var found=new HashMap<String,SyncIdentityStore.Identity>();
                for(var key:query.sourceKeys()){var row=stored.get(query.object()+":"+key);if(row!=null)found.put(key,row);}
                return found;
            });
            doAnswer(call->{
                for(var row:call.<List<SyncIdentityStore.Save>>getArgument(0))
                    stored.put(row.object()+":"+row.sourceKey(),new SyncIdentityStore.Identity(row.sourceKey(),row.targetId(),row.fingerprint()));
                return null;
            }).when(identities).saveAll(anyList());
            var task=new P01OrganizationSyncTask(f.session.getMapper(P01OrganizationSyncTask.TargetMapper.class));
            var preview=task.execute(f.context(true,1000,identities));
            assertEquals(4,preview.inserted());assertTrue(stored.isEmpty());
            assertTrue(f.session.getMapper(P01OrganizationSyncTask.TargetMapper.class)
                    .selectCompanies(new P01OrganizationSyncTask.CatalogueQuery(1,true)).isEmpty());
            var actual=task.execute(f.context(false,1000,identities));
            assertEquals(4,actual.inserted());assertEquals(4,stored.size());
            var before=Map.copyOf(stored);
            var replay=task.execute(f.context(false,1000,identities));
            assertEquals(0,replay.inserted());assertEquals(4,replay.unchanged());assertEquals(before,stored);
            f.sql("UPDATE system_dept SET name='人工维护名称' WHERE code='160000'");
            assertEquals("P01_FORMAL_DIRECTORY_MAINTAINED",
                    assertThrows(IllegalArgumentException.class,()->task.execute(f.context(false,1000,identities))).getMessage());
        }
    }

    @Test void bothSourceProjectionsParseWithRuntimeParserAndParameterizeLimit() throws Exception {
        try(var f=new Fixture()){
            for(var method:List.of("selectCompanies","selectDepartments")){
                var bound=f.session.getConfiguration().getMappedStatement(P01OrganizationSyncTask.SourceMapper.class.getName()+"."+method).getBoundSql(QUERY);
                OrganizationBackfillSqlTest.parseWithRuntimeStack(bound.getSql());
                assertEquals(1,bound.getParameterMappings().size());
                assertEquals("limit",bound.getParameterMappings().getFirst().getProperty());
                assertTrue(bound.getSql().stripTrailing().endsWith("LIMIT ?"));
            }
        }
    }
}
