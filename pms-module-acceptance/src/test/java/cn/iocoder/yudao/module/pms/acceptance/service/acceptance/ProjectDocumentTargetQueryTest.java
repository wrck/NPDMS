package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDocumentTargetQueryTest {
    @Test void frozenCodesIncludeTemplateInstancesWithoutLegacyDefinitionIdAndPreserveScope() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE acc_project_deliverable (id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,deliverable_code VARCHAR(64),name VARCHAR(128),stage_code VARCHAR(32),task_code VARCHAR(64),required BOOLEAN,source_definition_id BIGINT,status VARCHAR(32),current_source_version_id BIGINT,archive_status VARCHAR(32),version INT,deleted INT,creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.execute("INSERT INTO acc_project_deliverable (id,tenant_id,project_id,deliverable_code,source_definition_id,deleted) VALUES (1,7,9,'D1',NULL,0),(2,7,9,'LEGACY',44,0),(3,8,9,'D1',NULL,0),(4,7,10,'D1',NULL,0),(5,7,9,'D1',NULL,1)");
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment("document-targets", new JdbcTransactionFactory(), database));
            configuration.addMapper(AccProjectDeliverableMapper.class);
            try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true)) {
                var mapper = session.getMapper(AccProjectDeliverableMapper.class);
                var rows = mapper.selectDocuments(new AccProjectDeliverableMapper.DocumentScope(7L, 9L, Set.of("D1")));
                assertEquals(1, rows.size());
                assertEquals(1L, rows.getFirst().getId());
                assertNull(rows.getFirst().getSourceDefinitionId());
                assertTrue(mapper.selectDocuments(new AccProjectDeliverableMapper.DocumentScope(7L, 9L, Set.of())).isEmpty());
            }
        } finally { database.shutdown(); }
    }
}
