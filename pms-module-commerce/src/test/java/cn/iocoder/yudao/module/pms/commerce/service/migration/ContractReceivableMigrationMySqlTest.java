package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi.Entry;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractReceivableMigrationMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.ProjectCommerceMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class ContractReceivableMigrationMySqlTest {
    Connection db;org.apache.ibatis.session.SqlSession session;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/migration/ContractReceivableMigrationMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mapper/migration/ContractCollectionPlanMapper.xml"),
                new org.springframework.core.io.ClassPathResource("mapper/contract/ProjectCommerceMapper.xml"));
        session=factory.getObject().openSession(db);
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE receipt_probe LIKE com_contract_receivable");
            sql.execute("ALTER TABLE receipt_probe RENAME TO com_contract_receivable");
            sql.execute("CREATE TEMPORARY TABLE com_contract(id BIGINT,tenant_id BIGINT,contract_no VARCHAR(64),deleted BIT)");
            sql.execute("CREATE TEMPORARY TABLE com_project_contract_relation(id BIGINT,tenant_id BIGINT,project_id BIGINT,contract_id BIGINT,deleted BIT,status VARCHAR(32),effective_from DATETIME,effective_to DATETIME)");
        }
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null && !db.isClosed())db.close();}
    @Test void all52ExistingFieldsRoundTripAndOriginalMillisecondsSurvive() {
        var row=ContractReceivableMigrationService.entity(new Entry("11",ContractReceivableMigrationServiceTest.source(),"91","crmapp",null,null),1L);
        row.setId(1L);row.setMappingStatus("PENDING_CONTRACT");var mapper=session.getMapper(ContractReceivableMigrationMapper.class);
        assertEquals(1,mapper.insertReceipt(row));
        var read=mapper.selectSourceForUpdate(new ContractReceivableMigrationMapper.SourceQuery(1L,"CRM","11")).getFirst();
        assertEquals(ContractReceivableMigrationService.fields(row),ContractReceivableMigrationService.fields(read));
        assertTrue(mapper.selectSourceForUpdate(new ContractReceivableMigrationMapper.SourceQuery(2L,"CRM","11")).isEmpty());
        row.setCollectedAmount(new java.math.BigDecimal("100.12"));assertEquals(1,mapper.updateReceipt(row));
        assertEquals("100.12",ContractReceivableMigrationService.fields(mapper.selectSourceForUpdate(
                new ContractReceivableMigrationMapper.SourceQuery(1L,"CRM","11")).getFirst()).get("collectedAmount"));
    }
    @Test void normalProjectDisplayUsesExactContractNumberAndProtectsTenantAndEffectiveRelations() throws Exception {
        var mapper=session.getMapper(ContractReceivableMigrationMapper.class);
        var row=ContractReceivableMigrationService.entity(new Entry("11",ContractReceivableMigrationServiceTest.source(),"91","crmapp",null,null),1L);
        row.setId(1L);row.setMappingStatus("PENDING_CONTRACT");row.setContractId(null);mapper.insertReceipt(row);
        try(var sql=db.createStatement()) {
            sql.execute("INSERT INTO com_contract VALUES(91,1,'C-01',FALSE),(92,2,'C-01',FALSE)");
            sql.execute("INSERT INTO com_project_contract_relation VALUES(1,1,20,91,FALSE,'ACTIVE',NULL,NULL),(2,1,21,91,FALSE,'HISTORICAL',NULL,NULL),(3,2,22,92,FALSE,'ACTIVE',NULL,NULL)");
        }
        var queries=session.getMapper(ProjectCommerceMapper.class);
        assertEquals(List.of(1L),queries.selectReceivables(new ProjectCommerceQuery(1L,20L)).stream().map(x->x.getId()).toList());
        assertTrue(queries.selectReceivables(new ProjectCommerceQuery(1L,21L)).isEmpty());
        assertTrue(queries.selectReceivables(new ProjectCommerceQuery(2L,22L)).isEmpty());
        assertTrue(queries.selectReceivables(new ProjectCommerceQuery(1L,999L)).isEmpty());
    }
    @Test void indexedCandidateLookupStillExcludesCaseSpacesOtherTenantsAndDeletedContracts() throws Exception {
        try(var sql=db.createStatement()) {
            sql.execute("INSERT INTO com_contract VALUES(1,1,'C-01',FALSE),(2,1,'c-01',FALSE),(3,1,'C-01 ',FALSE),(4,2,'C-01',FALSE),(5,1,'C-01',TRUE)");
        }
        assertEquals(List.of(1L),session.getMapper(ContractReceivableMigrationMapper.class).selectExactContractIds(
                new ContractReceivableMigrationMapper.ContractQuery(1L,"C-01")));
        assertEquals(List.of(1L),session.getMapper(cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractCollectionPlanMapper.class)
                .selectExactContractIds(new cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractCollectionPlanMapper.ContractQuery(1L,"C-01")));
    }
}
