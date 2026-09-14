package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.PreviousBusinessAssociationsQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PreviousBusinessAssociationsMapperTest {
    @Test void nullAndOlderExecutionAssociationsAreHistoryAndEmptyCandidateSetDoesNotBroadenScope() throws Exception {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            try (var connection = database.getConnection(); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE proj_task_business_link (tenant_id BIGINT, project_id BIGINT, task_id BIGINT, "
                        + "owner_context VARCHAR(40), object_type VARCHAR(40), object_id VARCHAR(40), node_execution_id BIGINT)");
                sql.execute("INSERT INTO proj_task_business_link VALUES (1,9,11,'SOL','SITE_SURVEY','legacy',NULL),"
                        + "(1,9,11,'SOL','SITE_SURVEY','older',60),(1,9,11,'SOL','SITE_SURVEY','current',70),"
                        + "(2,9,11,'SOL','SITE_SURVEY','tenant',60),(1,8,11,'SOL','SITE_SURVEY','project',60),"
                        + "(1,9,12,'SOL','SITE_SURVEY','task',60),(1,9,11,'OTHER','SITE_SURVEY','owner',60)");
            }
            var configuration = new Configuration(new Environment("association-test", new JdbcTransactionFactory(), database));
            try (var xml = getClass().getClassLoader().getResourceAsStream("mapper/taskbusiness/ProjectTaskBusinessLinkMapper.xml")) {
                assertNotNull(xml);
                new XMLMapperBuilder(xml,configuration,"associations",configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
                var mapper = session.getMapper(ProjectTaskBusinessLinkMapper.class);
                assertEquals(Set.of("legacy","older"),Set.copyOf(mapper.selectPreviouslyAssociatedObjectIds(
                        new PreviousBusinessAssociationsQuery(1L,9L,11L,70L,"SOL","SITE_SURVEY",
                                Set.of("legacy","older","current","tenant","project","task","owner")))));
                assertTrue(mapper.selectPreviouslyAssociatedObjectIds(new PreviousBusinessAssociationsQuery(
                        1L,9L,11L,70L,"SOL","SITE_SURVEY",Set.of())).isEmpty());
            }
        } finally { database.shutdown(); }
    }
}
