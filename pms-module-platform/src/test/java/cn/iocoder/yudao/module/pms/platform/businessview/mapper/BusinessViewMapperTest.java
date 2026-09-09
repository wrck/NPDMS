package cn.iocoder.yudao.module.pms.platform.businessview.mapper;

import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.BusinessViewRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.businessview.query.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.businessview.BusinessViewRevisionDO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PM-03: exercises parsed Mapper XML and bind paths; does NOT claim actual MySQL/CAS execution. */
class BusinessViewMapperTest {
    private Configuration configuration() {
        var configuration = new Configuration();
        String resource = "mapper/businessview/BusinessViewRevisionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            return configuration;
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private String sql(String statement, Object parameters) {
        return configuration().getMappedStatement(BusinessViewRevisionMapper.class.getName() + "." + statement)
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim();
    }
    @Test void identityLockIsTenantScopedAndRevisionOrdered() {
        String sql = sql("selectIdentityForUpdate", Map.of("query", new BusinessViewIdentityQuery(1L, "TYPE", "view")));
        assertTrue(sql.contains("tenant_id = ?")); assertTrue(sql.contains("entity_type = ?")); assertTrue(sql.contains("view_key = ?"));
        assertTrue(sql.endsWith("ORDER BY revision_no, id FOR UPDATE")); assertTrue(sql.contains("deleted = b'0'"));
    }
    @Test void emptyOwnerScopeIsFalseInBothCountAndPageAndNeverDropsTenant() {
        var empty = new BusinessViewPageQuery(1L, null, null, Set.of(), 1, 10);
        for (String statement : Set.of("selectPageRows", "selectPageCount")) {
            String sql = sql(statement, Map.of("query", empty));
            assertTrue(sql.contains("AND 1 = 0")); assertTrue(sql.contains("tenant_id = ?"));
        }
        var query = new BusinessViewPageQuery(1L, "TYPE", "PAGE", Set.of("SOL"), 2, 10);
        String sql = sql("selectPageRows", Map.of("query", query));
        assertTrue(sql.contains("owner_context IN")); assertTrue(sql.contains("entity_type = ?"));
        assertTrue(sql.contains("ORDER BY entity_type, view_key, revision_no, id")); assertEquals(10L, query.getOffset());
    }
    @Test void lifecycleSqlPreservesBodyAndUsesExactRowVersion() {
        var row = new BusinessViewRevisionDO(); row.setId(1L); row.setTenantId(1L); row.setVersion(0);
        row.setPublishedAt(LocalDateTime.now()); row.setDisabledAt(LocalDateTime.now()); row.setUpdater("7");
        for (String statement : Set.of("publishIfMatch", "disableIfMatch", "updateDraftIfMatch")) {
            String sql = sql(statement, Map.of("row", row));
            assertTrue(sql.contains("id = ? AND tenant_id = ? AND version = ?"));
            assertTrue(sql.contains("version = version + 1")); assertTrue(sql.contains("disabled_at IS NULL"));
            if (!statement.equals("updateDraftIfMatch")) assertFalse(sql.contains("context_schema ="));
            else assertTrue(sql.contains("published_at IS NULL"));
        }
        assertTrue(sql("disableIfMatch", Map.of("row", row)).contains("published_at IS NOT NULL"));
        assertTrue(sql("publishIfMatch", Map.of("row", row)).contains("published_at IS NULL"));
    }
    @Test void mapperShortCircuitsEmptyScopeWithoutExecutingCountOrSelect() {
        var mapper = mock(BusinessViewRevisionMapper.class, CALLS_REAL_METHODS);
        assertEquals(0L, mapper.selectPage(new BusinessViewPageQuery(1L, null, null, Set.of(), 1, 10)).getTotal());
        verify(mapper, never()).selectPageRows(any()); verify(mapper, never()).selectPageCount(any());
    }
}
