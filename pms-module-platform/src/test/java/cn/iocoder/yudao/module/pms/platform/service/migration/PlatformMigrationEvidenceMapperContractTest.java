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

    @Test void bindsStagingCardinalityWithoutClassificationSubqueries() {
        Configuration configuration=configuration("MigrationSourceRecordMapper.xml");
        var params=new org.apache.ibatis.binding.MapperMethod.ParamMap<>();
        params.put("query",new MigrationBatchIdQuery(7L,22L));
        BoundSql sql=boundSql(configuration,
                "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.countBatchSources",params);
        assertEquals("SELECT COUNT(*) FROM plt_migration_source_record WHERE tenant_id = ? AND batch_id = ? AND deleted = 0",
                normalize(sql.getSql()));
        assertAllParametersResolvable(sql);
        assertEquals(List.of("query.tenantId","query.batchId"),
                sql.getParameterMappings().stream().map(ParameterMapping::getProperty).toList());
    }

    @Test void mappingPageUsesBatchIndexAndBindsAllScopePredicates() {
        Configuration config=configuration("ExternalKeyMappingMapper.xml");
        var params=new org.apache.ibatis.binding.MapperMethod.ParamMap<>();
        params.put("query",new MigrationSourcePageQuery(7L,22L,List.of(31L,32L)));
        BoundSql sql=boundSql(config,
                "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.ExternalKeyMappingMapper.selectSourcePage",params);
        String text=normalize(sql.getSql());
        assertTrue(text.contains("FROM plt_external_key_mapping FORCE INDEX (idx_plt_external_mapping_batch)"));
        assertTrue(text.contains("WHERE tenant_id = ? AND batch_id = ? AND deleted = 0"));
        assertTrue(text.contains("AND source_record_id IN"));
        assertEquals(4,sql.getParameterMappings().size());
        assertAllParametersResolvable(sql);
        assertEquals("query.tenantId",sql.getParameterMappings().get(0).getProperty());
        assertEquals("query.batchId",sql.getParameterMappings().get(1).getProperty());
        for(List<Long> ids:java.util.Arrays.asList(List.<Long>of(),null)) {
            params.put("query",new MigrationSourcePageQuery(7L,22L,ids));
            BoundSql empty=boundSql(config,
                    "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.ExternalKeyMappingMapper.selectSourcePage",params);
            assertTrue(normalize(empty.getSql()).endsWith("AND 1 = 0"));
            assertEquals(2,empty.getParameterMappings().size());
            assertAllParametersResolvable(empty);
        }
    }

    @Test void classificationUsesBoundedSourceAndCorrelatedEvidenceIndexes() {
        var config=configuration("MigrationSourceRecordMapper.xml");
        var params=new org.apache.ibatis.binding.MapperMethod.ParamMap<>();
        params.put("query",new MigrationBatchIdQuery(7L,22L));
        BoundSql sql=boundSql(config,
                "cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.MigrationSourceRecordMapper.selectClassificationSummary",params);
        String text=normalize(sql.getSql());
        assertTrue(text.contains("FROM plt_migration_source_record s FORCE INDEX (idx_plt_migration_source_cursor)"));
        assertEquals(2,text.split("FORCE INDEX \\(uk_plt_external_mapping_result\\)",-1).length-1);
        assertTrue(text.contains("FROM plt_migration_issue i FORCE INDEX (uk_plt_migration_issue_key)"));
        assertTrue(text.contains("has_mapped + has_issue + has_retained > 1"));
        assertEquals(2,sql.getParameterMappings().size());
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
