package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class DppmsObsoleteOrderLineMapperMySqlTest {
    Connection db;org.apache.ibatis.session.SqlSession session;
    @BeforeEach void setup() throws Exception {
        assertEquals("24306",System.getenv("NPDMS_MYSQL_PORT"));assertEquals("npdms_domain_test",System.getenv("NPDMS_DB_NAME"));
        String url="jdbc:mysql://127.0.0.1:24306/npdms_domain_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        db=DriverManager.getConnection(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"));
        var factory=new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD")));
        factory.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
        var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
        factory.setMapperLocations(new org.springframework.core.io.ClassPathResource("mapper/sync/SyncBindingMapper.xml"));
        session=factory.getObject().openSession(db);
        try(var sql=db.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE int_sync_binding(id BIGINT PRIMARY KEY,tenant_id BIGINT,task_id BIGINT,object_key VARCHAR(64),source_object VARCHAR(64),source_key VARCHAR(64),target_id BIGINT,fields_json JSON,deleted BIT)");
            sql.execute("INSERT INTO int_sync_binding VALUES(1,1,9,'LINE','pm_order_line_from_erp','1',71,JSON_OBJECT('companyCode','01'),FALSE),(2,1,9,'LINE','pm_order_line_from_erp','2',72,JSON_OBJECT('companyCode','001'),FALSE),(3,2,9,'LINE','pm_order_line_from_erp','3',73,JSON_OBJECT('companyCode','01'),FALSE),(4,1,8,'LINE','pm_order_line_from_erp','4',74,JSON_OBJECT('companyCode','01'),FALSE)");
        }
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null && !db.isClosed())db.close();}
    @Test void onlyRaw01FromDesignatedTaskAndTenantCanBeRemoved() {
        var mapper=session.getMapper(SyncBindingMapper.class);
        assertEquals(List.of(1L),mapper.selectObsoleteOrderLinesForUpdate(new SyncQueries.ObsoleteOrderLines(1L,9L,List.of("1","2","3","4"))).stream().map(x->x.getId()).toList());
        assertEquals(1,mapper.deleteObsoleteOrderLines(new SyncQueries.ObsoleteOrderLineDelete(1L,9L,List.of(1L,2L,3L,4L))));
        assertTrue(mapper.selectObsoleteOrderLinesForUpdate(new SyncQueries.ObsoleteOrderLines(1L,9L,List.of("1"))).isEmpty());
        assertEquals(List.of(3L),mapper.selectObsoleteOrderLinesForUpdate(new SyncQueries.ObsoleteOrderLines(2L,9L,List.of("3"))).stream().map(x->x.getId()).toList());
        assertEquals(List.of(4L),mapper.selectObsoleteOrderLinesForUpdate(new SyncQueries.ObsoleteOrderLines(1L,8L,List.of("4"))).stream().map(x->x.getId()).toList());
    }
    @Test void emptyFiltersCannotDeleteOrSelectEverything() {
        var mapper=session.getMapper(SyncBindingMapper.class);
        assertTrue(mapper.selectObsoleteOrderLinesForUpdate(new SyncQueries.ObsoleteOrderLines(1L,9L,List.of())).isEmpty());
        assertEquals(0,mapper.deleteObsoleteOrderLines(new SyncQueries.ObsoleteOrderLineDelete(1L,9L,List.of())));
    }
}
