package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.ReportResultInventoryQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the production join and paging SQL on H2; the real MySQL schema is not substituted by this fixture. */
class AcceptanceReportInventorySqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private AcceptanceReportVersionMapper reports;
    @BeforeEach void before() throws Exception {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:report_inventory_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE acc_acceptance(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,deleted INT)");
        jdbc.execute("INSERT INTO acc_acceptance VALUES(100,1,3,0),(101,1,3,0),(102,1,4,0),(103,2,3,0),(104,1,3,1)");
        jdbc.execute("CREATE TABLE acc_acceptance_report_version(id BIGINT PRIMARY KEY,tenant_id BIGINT,acceptance_id BIGINT,report_status VARCHAR(20),deleted INT)");
        jdbc.execute("INSERT INTO acc_acceptance_report_version VALUES(40,1,100,'EFFECTIVE',0),(41,1,100,'SUPERSEDED',0),(42,1,100,'REVOKED',0),(43,1,100,'DRAFT',0),(44,1,101,'EFFECTIVE',0),(45,2,100,'EFFECTIVE',0),(46,1,100,'EFFECTIVE',1),(47,1,104,'EFFECTIVE',0),(48,1,102,'EFFECTIVE',0),(49,2,103,'EFFECTIVE',0)");
        var cfg=new Configuration(new Environment("report-inventory-test",new JdbcTransactionFactory(),dataSource));
        String path="mapper/acceptancereport/AcceptanceReportVersionMapper.xml";
        try(var xml=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(xml);new XMLMapperBuilder(xml,cfg,path,cfg.getSqlFragments()).parse();}
        session=new SqlSessionFactoryBuilder().build(cfg).openSession(true);reports=session.getMapper(AcceptanceReportVersionMapper.class);
    }
    @AfterEach void after(){if(session!=null)session.close();jdbc.execute("SHUTDOWN");}
    @Test void scopeJoinPreservesNativeReportHistoryAndRejectsCrossTenantRows(){
        assertEquals(List.of("40","44"),reports.selectResultInventory(new ReportResultInventoryQuery(1L,3L,null,null,10,false)));
        assertEquals(List.of("40","41","42","44"),reports.selectResultInventory(new ReportResultInventoryQuery(1L,3L,null,null,10,true)));
    }
    @Test void objectScopeUsesAcceptanceIdsAndEmptyDoesNotMeanAll(){
        assertEquals(List.of("40","41","42"),reports.selectResultInventory(new ReportResultInventoryQuery(1L,3L,List.of(100L),null,10,true)));
        assertTrue(reports.selectResultInventory(new ReportResultInventoryQuery(1L,3L,List.of(),null,10,true)).isEmpty());
        assertTrue(reports.selectResultInventory(new ReportResultInventoryQuery(1L,3L,List.of(40L),null,10,true)).isEmpty());
    }
    @Test void stablePagingRetainsEachResultRatherThanSelectingFirstReport(){
        assertEquals(List.of("41","42"),reports.selectResultInventory(new ReportResultInventoryQuery(1L,3L,null,40L,2,true)));
        assertEquals(List.of("44"),reports.selectResultInventory(new ReportResultInventoryQuery(1L,3L,null,42L,2,true)));
    }
}
