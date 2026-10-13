package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="npdms.dppms-commerce-mapper.mysql",matches="true")
class SyncBindingRestorationMySqlTest {
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
            sql.execute("CREATE TEMPORARY TABLE binding_restore_probe LIKE int_sync_binding");
            sql.execute("ALTER TABLE binding_restore_probe RENAME TO int_sync_binding");
            sql.execute("INSERT INTO int_sync_binding(id,tenant_id,task_id,object_key,source_object,source_key,target_id,fields_json,last_run_id,deleted,target_shared) VALUES(1,1,9,'PROJECT','pm_project','10',71,JSON_OBJECT('project',JSON_OBJECT('id',71)),8,TRUE,FALSE)");
        }
    }
    @AfterEach void close() throws Exception {if(session!=null)session.close();if(db!=null&&!db.isClosed())db.close();}
    @Test void actualSourceUniqueKeyRequiresReusingRetiredBindingId() throws Exception {
        var mapper=session.getMapper(SyncBindingMapper.class);
        var old=mapper.selectRetiredSourceKeysForUpdate(new SyncQueries.SourceKeys(1L,9L,"PROJECT","pm_project",List.of("10"))).getFirst();
        try(var sql=db.createStatement()) {
            assertThrows(SQLIntegrityConstraintViolationException.class,()->sql.execute("INSERT INTO int_sync_binding(id,tenant_id,task_id,object_key,source_object,source_key,target_id,fields_json,last_run_id,target_shared) VALUES(2,1,9,'PROJECT','pm_project','10',71,JSON_OBJECT(),8,TRUE)"));
        }
        assertEquals(1,mapper.restoreVerifiedBinding(old));assertEquals(0,mapper.restoreVerifiedBinding(old));
        var restored=mapper.selectSourceKeys(new SyncQueries.SourceKeys(1L,9L,"PROJECT","pm_project",List.of("10"))).getFirst();
        assertEquals(1L,restored.getId());assertEquals(old.getFieldsJson(),restored.getFieldsJson());assertEquals(8L,restored.getLastRunId());
    }
    @Test void changedFactsAndWrongTenantCannotRestore() {
        var mapper=session.getMapper(SyncBindingMapper.class);
        assertTrue(mapper.selectRetiredSourceKeysForUpdate(new SyncQueries.SourceKeys(1L,9L,"PROJECT","pm_project",List.of())).isEmpty());
        var old=mapper.selectRetiredSourceKeysForUpdate(new SyncQueries.SourceKeys(1L,9L,"PROJECT","pm_project",List.of("10"))).getFirst();
        old.setTenantId(2L);assertEquals(0,mapper.restoreVerifiedBinding(old));old.setTenantId(1L);
        old.setFieldsJson("{}");assertEquals(0,mapper.restoreVerifiedBinding(old));
        assertEquals(1,mapper.selectRetiredSourceKeysForUpdate(new SyncQueries.SourceKeys(1L,9L,"PROJECT","pm_project",List.of("10"))).size());
    }
}
