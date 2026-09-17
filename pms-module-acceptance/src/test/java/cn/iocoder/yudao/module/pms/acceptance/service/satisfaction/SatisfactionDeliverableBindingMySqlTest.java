package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/** All writes, including V241, target connection-local TEMPORARY tables, never permanent business tables. */
@EnabledIfSystemProperty(named="pms.satisfaction.binding.mysql",matches="true")
class SatisfactionDeliverableBindingMySqlTest {
    @Test void migrationAndOwnerQueriesPreserveFrozenIdentityAndScope() throws Exception {
        assertEquals("npdms_test",System.getenv("NPDMS_DB_NAME"));
        assertEquals("23316",System.getenv("NPDMS_MYSQL_PORT"));
        try (var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                System.getenv("NPDMS_DB_USER"),System.getenv("NPDMS_DB_PASSWORD"))) {
            var database=new SingleConnectionDataSource(connection,true);
            var jdbc=new JdbcTemplate(database);
            jdbc.execute("CREATE TEMPORARY TABLE acc_satisfaction_collection_task (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,project_task_id BIGINT,task_status VARCHAR(32),version INT,deleted BIT DEFAULT 0)");
            jdbc.execute("CREATE TEMPORARY TABLE acc_project_deliverable (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,task_code VARCHAR(64),deliverable_code VARCHAR(64),deleted BIT DEFAULT 0)");
            jdbc.execute("INSERT INTO acc_satisfaction_collection_task VALUES (1,7,80,81,'COMPLETED',3,0)");
            var migration=Path.of("../sql/migrations/V241__satisfaction_deliverable_instance_binding.sql");
            if (!Files.exists(migration)) migration=Path.of("sql/migrations/V241__satisfaction_deliverable_instance_binding.sql");
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(migration));
            assertNull(jdbc.queryForObject("SELECT deliverable_id FROM acc_satisfaction_collection_task WHERE id=1",Long.class));
            assertEquals("COMPLETED",jdbc.queryForObject("SELECT task_status FROM acc_satisfaction_collection_task WHERE id=1",String.class));
            assertEquals(3,jdbc.queryForObject("SELECT version FROM acc_satisfaction_collection_task WHERE id=1",Integer.class));
            jdbc.execute("INSERT INTO acc_satisfaction_collection_task(id,tenant_id,project_id,project_task_id,task_status,version,deliverable_id) VALUES (2,7,80,81,'PENDING_COLLECTION',0,501)");
            jdbc.execute("INSERT INTO acc_project_deliverable VALUES (501,7,80,'CUSTOM','CUSTOM-REPORT',0),(502,8,80,'CUSTOM','CUSTOM-REPORT',0),(503,7,90,'CUSTOM','CUSTOM-REPORT',0),(504,7,80,'CUSTOM','OLD',1),(505,7,80,'OTHER','OTHER',0)");
            var configuration=new Configuration(new Environment("temporary-satisfaction-binding",new SpringManagedTransactionFactory(),database));
            configuration.setMapUnderscoreToCamelCase(true);
            for (String resource:java.util.List.of("mapper/satisfaction/SatisfactionCollectionTaskMapper.xml","mapper/acceptance/AccProjectDeliverableMapper.xml")) {
                try (var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
                    assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
                }
            }
            var session=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
            var tasks=session.getMapper(SatisfactionCollectionTaskMapper.class);
            var deliverables=session.getMapper(AccProjectDeliverableMapper.class);
            new TransactionTemplate(new DataSourceTransactionManager(database)).executeWithoutResult(status -> {
                assertEquals(501L,tasks.selectByIdForUpdate(7L,2L).getDeliverableId());
                assertNull(tasks.selectByIdForUpdate(8L,2L));
                var rows=deliverables.selectTaskDeliverablesForUpdate(new AccProjectDeliverableMapper.TaskDeliverablesQuery(7L,80L,"CUSTOM"));
                assertEquals(1,rows.size()); assertEquals(501L,rows.getFirst().getId());
            });
        }
    }
}
