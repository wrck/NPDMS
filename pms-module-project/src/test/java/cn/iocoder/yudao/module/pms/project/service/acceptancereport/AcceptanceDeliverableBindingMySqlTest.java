package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdentityLockQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/** V240 runs exclusively against a shadow TEMPORARY table on this single connection. */
@EnabledIfSystemProperty(named="pms.acceptance.binding.mysql",matches="true")
class AcceptanceDeliverableBindingMySqlTest {
    @Test void migrationDoesNotInferHistoryAndOwnerQueriesReadTheFrozenInstance() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME")); assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database=new SingleConnectionDataSource(connection,true); var jdbc=new JdbcTemplate(database);
            jdbc.execute("CREATE TEMPORARY TABLE acc_acceptance (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,project_task_id BIGINT,execution_contract_id BIGINT,acceptance_type VARCHAR(32),activity_status VARCHAR(32),current_report_version_id BIGINT,version INT,creator VARCHAR(64),updater VARCHAR(64),create_time DATETIME,update_time DATETIME,deleted BIT DEFAULT 0)");
            jdbc.execute("INSERT INTO acc_acceptance(id,tenant_id,project_id,acceptance_type,activity_status,version) VALUES (1,7,80,'PRELIMINARY','COMPLETED',3)");
            var migration=Path.of("../sql/migrations/V240__acceptance_deliverable_instance_binding.sql");
            if (!Files.exists(migration)) migration=Path.of("sql/migrations/V240__acceptance_deliverable_instance_binding.sql");
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(migration));
            assertNull(jdbc.queryForObject("SELECT deliverable_id FROM acc_acceptance WHERE id=1",Long.class));
            assertEquals("COMPLETED",jdbc.queryForObject("SELECT activity_status FROM acc_acceptance WHERE id=1",String.class));
            assertEquals(3,jdbc.queryForObject("SELECT version FROM acc_acceptance WHERE id=1",Integer.class));
            jdbc.execute("INSERT INTO acc_acceptance(id,tenant_id,project_id,deliverable_id,acceptance_type,activity_status,version) VALUES (2,7,81,501,'FINAL','PENDING',0),(3,8,81,502,'FINAL','PENDING',0)");
            var configuration=new Configuration(new Environment("temporary-acceptance-binding",new SpringManagedTransactionFactory(),database));
            configuration.setMapUnderscoreToCamelCase(true);
            String resource="mapper/acceptancereport/AcceptanceActivityMapper.xml";
            try (var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
            }
            var mapper=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(AcceptanceActivityMapper.class);
            var transaction=new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(database));
            transaction.executeWithoutResult(status -> {
                assertEquals(501L,mapper.selectByIdForUpdate(new AcceptanceActivityIdLockQuery(7L,2L)).getDeliverableId());
                assertNull(mapper.selectByIdForUpdate(new AcceptanceActivityIdLockQuery(8L,2L)));
                assertEquals(502L,mapper.selectByIdentityForUpdate(new AcceptanceActivityIdentityLockQuery(8L,81L,"FINAL")).getDeliverableId());
            });
        }
    }
}
