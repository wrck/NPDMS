package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi.Relation;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ProjectContractRelationMigrationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractMigrationIdentityQuery;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.time.LocalDateTime;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="npdms.project-contract-relation.mysql",matches="true")
class ProjectContractRelationMigrationMySqlTest {
    Connection db;SqlSession session;ProjectContractRelationMigrationMapper mapper;
    final LocalDateTime old=LocalDateTime.parse("2022-12-01T10:05:40.123");
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        db=DriverManager.getConnection("jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        String ddl;
        try(var statement=db.createStatement();var rows=statement.executeQuery("SHOW CREATE TABLE com_project_contract_relation")) {
            assertTrue(rows.next());ddl=rows.getString(2).replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE")
                    .replaceAll("(?m)^.*FOREIGN KEY.*\\n", "").replaceAll(",\\s*\\) ENGINE", "\n) ENGINE");
        }
        // Session-local copy preserves columns/checks/indexes; MySQL temporary tables cannot own foreign keys.
        try(var statement=db.createStatement()) {
            statement.execute(ddl);
            statement.execute(Files.readString(Path.of(System.getenv("NPDMS_CONTRACT_RELATION_DDL"))));
            statement.execute(Files.readString(Path.of(System.getenv("NPDMS_CONTRACT_RELATION_COMMENT_DDL"))));
            statement.execute("CREATE TEMPORARY TABLE com_contract (id BIGINT PRIMARY KEY, tenant_id BIGINT, company_code VARCHAR(20), deleted BIT, contract_no VARCHAR(100), contract_name VARCHAR(100), company_name VARCHAR(100))");
            statement.execute("INSERT INTO com_contract VALUES (11,1,'001',0,'C1','合同一','公司一'),(12,1,'002',0,'C2','合同二','公司二'),(13,1,'001',1,'C3','合同三','公司一'),(14,2,'001',0,'C4','合同四','公司一')");
        }
        db.setAutoCommit(false);
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.SingleConnectionDataSource(db,true));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config,com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.defaults());
        config.addMapper(ContractMapper.class);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/migration/ProjectContractRelationMigrationMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mapper/contract/ProjectContractRelationMapper.xml"));
        session=factory.getObject().openSession(db);mapper=session.getMapper(ProjectContractRelationMigrationMapper.class);
    }
    Relation row(long id,Long contract,String creator,String updater,int version) {
        return new Relation(id,1L,10L,contract,"RELATED","PMS","COM_PROJECT_CONTRACT_RELATION","op1",old,null,"ACTIVE",version,creator,old,updater,old,false);
    }
    ProjectContractRelationMigrationMapper.IdentityQuery identity() { return new ProjectContractRelationMigrationMapper.IdentityQuery(1L,10L,11L); }
    @AfterEach void close() throws Exception { if(session!=null) session.close();if(db!=null && !db.isClosed()) db.close(); }
    @Test void allSeventeenColumnsAndNullActorsRoundTripWithoutFrameworkFill() {
        var expected=row(88001,11L,null,null,0);assertEquals(1,mapper.insertHistorical(expected));
        assertEquals(expected,mapper.selectIdentityForUpdate(identity()));
        assertNull(mapper.selectIdentity(new ProjectContractRelationMigrationMapper.IdentityQuery(2L,10L,11L)));
    }
    @Test void approvedRawAuditCodesRoundTripInExistingNamedColumns() {
        var expected=row(88001,11L,"sys","异常数据处理",0);
        assertEquals(1,mapper.insertHistorical(expected));assertEquals(expected,mapper.selectIdentityForUpdate(identity()));
    }
    @Test void formalContractReferenceQueryKeepsTenantCompanyDeletionAndAllAmbiguousCandidates() throws Exception {
        var contracts=session.getMapper(ContractMapper.class);
        assertEquals(11L,contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(1L,"001","C1")).getFirst().getId());
        assertTrue(contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(1L,"002","C1")).isEmpty());
        assertTrue(contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(2L,"001","C1")).isEmpty());
        assertTrue(contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(1L,"001","C3")).isEmpty());
        assertTrue(contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(1L,null,"C1")).isEmpty());
        assertTrue(contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(1L,"001",null)).isEmpty());
        try(var statement=db.createStatement()) { statement.execute("INSERT INTO com_contract VALUES (15,1,'001',0,'C1','同号合同','公司一')"); }
        session.clearCache();assertEquals(2,contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(1L,"001","C1")).size());
        db.rollback();session.clearCache();assertEquals(1,contracts.selectMigrationIdentity(new ContractMigrationIdentityQuery(1L,"001","C1")).size());
    }
    @Test void limitedCorrectionPreservesSourceAndChangesDatesWithoutOnUpdateOverride() {
        var before=row(88001,11L,"1","1",4);mapper.insertHistorical(before);
        var time=old.minusYears(1);var after=new Relation(before.id(),1L,10L,11L,"RELATED",before.sourceSystem(),before.sourceTable(),
                before.sourceRecordKey(),time,null,"ACTIVE",5,null,time,null,time,false);
        assertEquals(1,mapper.correctHistorical(new ProjectContractRelationMigrationMapper.HistoricalWrite(before,after)));
        session.clearCache();assertEquals(after,mapper.selectIdentityForUpdate(identity()));
        assertEquals(0,mapper.correctHistorical(new ProjectContractRelationMigrationMapper.HistoricalWrite(before,after)));
    }
    @Test void existingUniqueAndRequiredContractConstraintsStillRejectInvalidRows() {
        mapper.insertHistorical(row(88001,11L,null,null,0));
        assertThrows(RuntimeException.class,()->mapper.insertHistorical(row(88002,11L,null,null,0)));
        assertThrows(RuntimeException.class,()->mapper.insertHistorical(row(88003,null,null,null,0)));
    }
    @Test void restorationOnlyChangesDeletionAndVersionAndKeepsOriginalAuditOnRollbackAndReplay() {
        var before=new Relation(88001L,1L,10L,11L,"RELATED","DPPMS","pm_project_contract","100:200",
                old,null,"HISTORICAL",1,null,old,null,old,true);
        mapper.insertHistorical(before);session.commit();
        var after=new cn.iocoder.yudao.module.pms.commerce.api.migration.ProjectContractRelationMigrationApi.Restoration(before).restored();
        var write=new ProjectContractRelationMigrationMapper.HistoricalWrite(before,after);
        assertEquals(1,mapper.restoreWithdrawn(write));session.clearCache();assertEquals(after,mapper.selectIdentityForUpdate(identity()));
        assertEquals(0,mapper.restoreWithdrawn(write));session.rollback();session.clearCache();
        assertEquals(before,mapper.selectIdentityForUpdate(identity()));
        var otherTenant=new Relation(before.id(),2L,10L,11L,"RELATED",before.sourceSystem(),before.sourceTable(),before.sourceRecordKey(),
                old,null,"HISTORICAL",1,null,old,null,old,true);
        assertEquals(0,mapper.restoreWithdrawn(new ProjectContractRelationMigrationMapper.HistoricalWrite(otherTenant,after)));
    }
    @Test void rollbackLeavesNoRelationship() {
        mapper.insertHistorical(row(88001,11L,null,null,0));session.rollback();session.clearCache();assertNull(mapper.selectIdentity(identity()));
    }
    @Test void originalAuditorCommentsAndNullableDefinitionsArePreserved() throws Exception {
        try(var statement=db.createStatement();var rows=statement.executeQuery("SHOW FULL COLUMNS FROM com_project_contract_relation")) {
            int total=0;
            while(rows.next()) {
                total++;
                if("creator".equals(rows.getString("Field"))) { assertEquals("YES",rows.getString("Null"));assertEquals("创建人",rows.getString("Comment")); }
                if("updater".equals(rows.getString("Field"))) { assertEquals("YES",rows.getString("Null"));assertEquals("更新人",rows.getString("Comment")); }
            }
            assertEquals(17,total);
        }
    }

    @Test void historicalRowsRemainOutsideCurrentQueryAndRespectProjectTenantCompanyAndDeletion() {
        var current=row(88001,11L,null,null,0);
        var historical=new Relation(88002L,1L,20L,11L,"RELATED","DPPMS","pm_project_contract","41325:37632",
                old,null,"HISTORICAL",0,null,old,null,old,false);
        mapper.insertHistorical(current);mapper.insertHistorical(historical);
        for(long contract:new long[]{12,13,14}) mapper.insertHistorical(new Relation(88002+contract,1L,20L,contract,"RELATED",
                "DPPMS","pm_project_contract","x",old,null,"HISTORICAL",0,null,old,null,old,false));
        var reader=session.getMapper(cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectContractRelationMapper.class);
        var query=new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractRelationPageQuery(1L,20L,java.util.List.of("001"),0,20);
        assertEquals(1,reader.selectCountByProject(query));assertEquals(88002L,reader.selectPageByProject(query).getFirst().getId());
        assertEquals(1,reader.selectCurrentByContract(new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractRelationListQuery(1L,11L)).size());
        var currentPage=new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractRelationPageQuery(1L,10L,java.util.List.of("001"),0,20);
        assertEquals(1,reader.selectCountByProject(currentPage));
        assertEquals("ACTIVE",reader.selectPageByProject(currentPage).getFirst().getStatus());
        assertEquals("合同一",reader.selectPageByProject(currentPage).getFirst().getContractName());
        for(var invalid:java.util.List.of(
                new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractRelationPageQuery(1L,20L,java.util.List.of(),0,20),
                new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractRelationPageQuery(1L,20L,null,0,20),
                new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractRelationPageQuery(2L,20L,java.util.List.of("001"),0,20),
                new cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractRelationPageQuery(1L,99L,java.util.List.of("001"),0,20))) {
            assertEquals(0,reader.selectCountByProject(invalid));assertTrue(reader.selectPageByProject(invalid).isEmpty());
        }
        assertEquals(historical,mapper.selectIdentity(new ProjectContractRelationMigrationMapper.IdentityQuery(1L,20L,11L)));
    }
}
