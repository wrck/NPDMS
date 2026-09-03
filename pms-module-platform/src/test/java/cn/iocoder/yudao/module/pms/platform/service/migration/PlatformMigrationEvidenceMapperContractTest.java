package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlatformMigrationEvidenceMapperContractTest {

    @Test
    void bindsClaimCollectionsAndKeepsStableLockOrder() {
        Configuration configuration = configuration("MigrationBatchMapper.xml");
        MigrationBatchClaimQuery query = new MigrationBatchClaimQuery(
                7L, "COM", "F-COM-001", List.of("ERP", "CRM"), List.of("orders", "contracts"));

        BoundSql boundSql = boundSql(configuration,
                "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationBatchMapper.selectNextStagedForUpdate",
                new org.apache.ibatis.binding.MapperMethod.ParamMap<>() {{ put("query", query); }});

        assertTrue(normalize(boundSql.getSql()).contains("ORDER BY create_time ASC, id ASC LIMIT 1 FOR UPDATE SKIP LOCKED"));
        assertAllParametersResolvable(boundSql);
    }

    @Test
    void bindsCursorAndClassificationQueriesWithoutUnsafeSql() {
        Configuration configuration = configuration("MigrationSourceRecordMapper.xml");
        var cursorParams = new org.apache.ibatis.binding.MapperMethod.ParamMap<>();
        cursorParams.put("query", new MigrationSourceCursorQuery(7L, 22L, 31L, 50));
        BoundSql cursor = boundSql(configuration,
                "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectCursorPage",
                cursorParams);
        var summaryParams = new org.apache.ibatis.binding.MapperMethod.ParamMap<>();
        summaryParams.put("query", new MigrationBatchIdQuery(7L, 22L));
        BoundSql summary = boundSql(configuration,
                "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectClassificationSummary",
                summaryParams);

        assertTrue(normalize(cursor.getSql()).contains("s.id > ? ORDER BY s.id ASC LIMIT ?"));
        assertTrue(normalize(summary.getSql()).contains("has_mapped + has_issue + has_retained > 1"));
        assertAllParametersResolvable(cursor);
        assertAllParametersResolvable(summary);
    }

    @Test
    void bindsIssueClosureCasFields() {
        Configuration configuration = configuration("MigrationIssueMapper.xml");
        var params = new org.apache.ibatis.binding.MapperMethod.ParamMap<>();
        params.put("update", new MigrationIssueCloseUpdate(
                7L, 41L, 2, 9L, "rules-v1", "{\"result\":\"retained\"}",
                java.time.LocalDateTime.of(2026, 8, 30, 20, 0)));
        BoundSql sql = boundSql(configuration,
                "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationIssueMapper.close", params);

        assertTrue(normalize(sql.getSql()).contains("issue_status = 'OPEN' AND version = ?"));
        assertAllParametersResolvable(sql);
    }

    private static Configuration configuration(String resourceName) {
        Configuration configuration = new Configuration();
        String resource = "mapper/migration/" + resourceName;
        try (InputStream input = PlatformMigrationEvidenceMapperContractTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static BoundSql boundSql(Configuration configuration, String statementId, Object parameter) {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        return statement.getBoundSql(parameter);
    }

    private static void assertAllParametersResolvable(BoundSql boundSql) {
        MetaObject parameters = SystemMetaObject.forObject(boundSql.getParameterObject());
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String property = mapping.getProperty();
            assertTrue(boundSql.hasAdditionalParameter(property) || parameters.hasGetter(property), property);
        }
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
