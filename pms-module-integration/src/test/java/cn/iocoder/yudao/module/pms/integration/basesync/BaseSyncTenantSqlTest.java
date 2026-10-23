package cn.iocoder.yudao.module.pms.integration.basesync;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production tenant INSERT rewrite, which plain XML/JDBC tests do not run. */
class BaseSyncTenantSqlTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void conditionalBindingBatchParsesWithTenantAndOldFingerprintGuards() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/basesync/BaseSyncMetadataMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(
                "cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.updateBindings");
        var rows = java.util.stream.IntStream.range(0, 2000).mapToObj(id ->
                new cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.BindingWrite(
                        id + 1, 7, 2, 3, "PRODUCT_SCOPE", Integer.toString(id), id + 1,
                        "b".repeat(64), "a".repeat(64), "actor")).toList();
        var parameter = Map.of("command", new cn.iocoder.yudao.module.pms.integration.dal.mysql.basesync.BaseSyncMetadataMapper.BindingBatch(rows));
        var bound = statement.getBoundSql(parameter);
        String rewritten = interceptor().parserMulti(bound.getSql(), null);
        assertEquals(bound.getParameterMappings().size(), rewritten.chars().filter(c -> c == '?').count());
        String compact = rewritten.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        for (String guard : List.of("binding_row.tenant_id=incoming.tenant_id", "binding_row.task_id=incoming.task_id",
                "binding_row.target_id=incoming.target_id", "binding_row.deleted=false", "binding_row.tenant_id=7",
                "collateutf8mb4_bin", "incoming.previous_fingerprint")) assertTrue(compact.contains(guard), guard);
    }

    private TenantLineInnerInterceptor interceptor() {
        TenantContextHolder.setTenantId(7L);
        return new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties()) {
            // Exercise every fixture as a tenant-owned table, without changing application registration.
            @Override public boolean ignoreTable(String tableName) { return false; }
        });
    }

    private Configuration configuration(List<String> tasks) throws Exception {
        var configuration = new Configuration();
        for (var task : tasks) {
            String resource = "mapper/basesync/" + task + "TargetMapper.xml";
            try (var input = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    @Test void tenantRewriteKeepsOneTenantColumnAndBothExplicitBatchValues() throws Exception {
        var configuration = configuration(List.of("P04", "P10", "P14", "P15", "P16", "P19", "P20",
                "P21", "P24", "P30", "P31", "P33", "P34", "P35", "P37"));
        var interceptor = interceptor();
        var parameters = Map.of("rows", List.of(new Object(), new Object()));
        int checked = 0;
        for (var name : configuration.getMappedStatementNames().stream().filter(value -> value.contains(".")).toList()) {
            var statement = configuration.getMappedStatement(name);
            if (statement.getSqlCommandType() != SqlCommandType.INSERT) continue;
            String original = statement.getBoundSql(parameters).getSql();
            String rewritten = interceptor.parserMulti(original, null);
            var insert = (Insert) CCJSqlParserUtil.parse(rewritten);
            assertEquals(1, insert.getColumns().stream()
                    .filter(column -> column.getColumnName().replace("`", "").equalsIgnoreCase("tenant_id"))
                    .count(), statement.getId());
            assertEquals(original.chars().filter(value -> value == '?').count(),
                    rewritten.chars().filter(value -> value == '?').count(), statement.getId());
            assertEquals(((Insert) CCJSqlParserUtil.parse(original)).getColumns().size(),
                    insert.getColumns().size(), statement.getId());
            checked++;
        }
        assertEquals(30, checked, "All affected and dependent inserts, including post-processing, must be exercised");
    }

    @Test void crmUpsertParsesThroughTenantInterceptorAndRetainsProtectedIdentity() throws Exception {
        var configuration = configuration(List.of("P11"));
        var statement = configuration.getMappedStatement(
                "cn.iocoder.yudao.module.pms.integration.basesync.task.P11CrmProductSyncTask$TargetMapper.insertCrmProduct");
        String original = statement.getBoundSql(Map.of("rows", List.of(new Object(), new Object()))).getSql();
        String rewritten = interceptor().parserMulti(original, null);
        String compact = rewritten.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        assertTrue(compact.contains("ast_product_official_info.tenant_id=values(tenant_id)"));
        assertTrue(compact.contains("cast(ast_product_official_info.product_codeasbinary)=cast(values(product_code)asbinary)"));
        assertTrue(compact.contains("ast_product_official_info.deleted=false"));
        assertTrue(compact.contains("cast(ast_product_official_info.source_systemasbinary)in(cast('crm'asbinary),cast('dppms'asbinary))"));
        assertTrue(compact.contains("thenast_product_official_info.idelsenullend"));
        assertTrue(compact.contains("version=ast_product_official_info.version+1"));
        assertEquals(original.chars().filter(value -> value == '?').count(),
                rewritten.chars().filter(value -> value == '?').count());
    }

    @Test void deliveryFormalContractQueryParsesBeforeLongHistoryExecution() throws Exception {
        var configuration = configuration(List.of("P15"));
        var statement = configuration.getMappedStatement(
                "cn.iocoder.yudao.module.pms.integration.basesync.task.P15DeliverySyncTask$TargetMapper.selectContract");
        var parameters = new cn.iocoder.yudao.module.pms.integration.api.basesync.AbstractSyncTask.SourceQuery<>(
                5_000_001, 7L, false,
                new cn.iocoder.yudao.module.pms.integration.basesync.task.P15DeliverySyncTask.Parameters(Map.of(), 31L));
        String original = statement.getBoundSql(parameters).getSql();
        String rewritten = interceptor().parserSingle(original, null);
        assertEquals(original.chars().filter(value -> value == '?').count(),
                rewritten.chars().filter(value -> value == '?').count());
        assertTrue(rewritten.contains("com_sales_order"));
        assertTrue(rewritten.contains("com_contract_receivable"));
    }
    @Test void publishedCursorRetainsPinnedTaskIndexJoinAndTenantGuards() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/basesync/BaseSyncPublishedProjectsMapper.xml";
        try (var input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(BaseSyncPublishedProjects.ReferenceMapper.class.getName() + ".selectPage");
        var parameters = Map.of("query", new BaseSyncPublishedProjects.Page(7, 3, 10, 1, ""));
        var bound = statement.getBoundSql(parameters);
        String original = bound.getSql();
        String rewritten = interceptor().parserSingle(original, null);
        assertNotNull(CCJSqlParserUtil.parse(original));
        assertNotNull(CCJSqlParserUtil.parse(rewritten));
        assertEquals(bound.getParameterMappings().size(), original.chars().filter(value -> value == '?').count());
        assertEquals(original.chars().filter(value -> value == '?').count(), rewritten.chars().filter(value -> value == '?').count());
        String compact = rewritten.replaceAll("\\s+", "").replace("`", "").toLowerCase(java.util.Locale.ROOT);
        assertTrue(compact.contains("straight_join"), rewritten);
        assertTrue(compact.contains("forceindex(uk_int_base_sync_binding_source)"));
        for (String alias : List.of("b", "t", "p")) {
            assertTrue(compact.contains(alias + ".tenant_id=7"), alias + " must retain the tenant interceptor guard");
        }
    }

}
