package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider.Query;
import cn.iocoder.yudao.module.pms.project.dal.mysql.customerreference.ProjectProjectCustomerReferenceMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.Connection;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 固定库、独立事务中的临时交付件；结束时全部回滚，不修改既有项目或历史。 */
@EnabledIfSystemProperty(named="skipITs", matches="false")
class ProjectCustomerReferenceSqlMySqlTest {
    @Test void ignoresAutomaticEmptyInstancesButProtectsActualContentAndOtherTenants() throws Exception {
        assertEquals("npdms_test", System.getenv("NPDMS_DB_NAME"));
        assertEquals("23316", System.getenv("NPDMS_MYSQL_PORT"));
        var ds = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://127.0.0.1:23316/npdms_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8",
                System.getenv("NPDMS_DB_USER"), System.getenv("NPDMS_DB_PASSWORD"));
        var configuration = new Configuration(new Environment("customer-correction-test", new JdbcTransactionFactory(), ds));
        configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        for (String resource : java.util.List.of("mapper/customerreference/ProjectProjectCustomerReferenceMapper.xml",
                "mapper/customerreference/PlatformProjectCustomerReferenceMapper.xml")) {
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
            Connection connection = session.getConnection();
            try {
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT DATABASE()")) {
                    assertTrue(result.next()); assertEquals("npdms_test", result.getString(1));
                }
                long projectId = 977_030_000_000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000L);
                var mapper = session.getMapper(ProjectProjectCustomerReferenceMapper.class);
                var query = new Query(1L, projectId);
                assertEquals(0, mapper.countReferences(query));
                assertEquals(0, session.getMapper(cn.iocoder.yudao.module.pms.platform.dal.mysql.customerreference.PlatformProjectCustomerReferenceMapper.class)
                        .countReferences(new cn.iocoder.yudao.module.pms.platform.dal.mysql.customerreference.PlatformProjectCustomerReferenceMapper.Query(1L, String.valueOf(projectId))));
                try (var statement = connection.prepareStatement("""
                        INSERT INTO acc_project_deliverable(project_id,deliverable_code,name,stage_code,source_definition_id,status,version,tenant_id,creator)
                        VALUES (?,'Z02-REF-TEST','自动空交付件','S1',1,'PENDING',0,1,'z02-ref-sql-test')
                        """)) {
                    statement.setLong(1, projectId); statement.executeUpdate();
                }
                assertEquals(0, mapper.countReferences(query));
                try (var statement = connection.prepareStatement("UPDATE acc_project_deliverable SET version=1 WHERE project_id=? AND creator='z02-ref-sql-test'")) {
                    statement.setLong(1, projectId); statement.executeUpdate();
                }
                assertEquals(1, mapper.countReferences(query));
                assertEquals(0, mapper.countReferences(new Query(2L, projectId)));
            } finally { connection.rollback(); }
        }
    }
}
