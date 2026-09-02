package cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.query.CutoverDashboardCandidateQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CutoverDashboardMapperContractTest {

    @Test
    void bindsTrustedScopeCursorAndCurrentApprovalProjection() throws Exception {
        Configuration configuration = new Configuration();
        Path path = Path.of("src/main/resources/mapper/dashboard/CutoverDashboardCandidateMapper.xml");
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
        }
        CutoverDashboardCandidateQuery query = new CutoverDashboardCandidateQuery(
                1L, Set.of(101L, 102L), 50L, 500);
        Map<String, Object> parameters = Map.of("query", query);
        BoundSql boundSql = configuration.getMappedStatement(CutoverDashboardCandidateMapper.class.getName()
                + ".selectBatchScoped").getBoundSql(parameters);
        Map<String, Object> boundValues = new LinkedHashMap<>();
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            String property = mapping.getProperty();
            Object value = boundSql.hasAdditionalParameter(property)
                    ? boundSql.getAdditionalParameter(property)
                    : configuration.newMetaObject(parameters).getValue(property);
            boundValues.put(property, value);
        }

        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql).contains("t.tenant_id = ?", "t.deleted = b'0'", "t.id > ?",
                "t.project_id IN", "i.replacement_approval_instance_id IS NULL",
                "ORDER BY t.id ASC", "LIMIT ?");
        assertThat(sql).doesNotContain("${");
        assertThat(boundValues.values()).contains(1L, 50L, 101L, 102L, 500);
    }

    @Test
    void doesNotExecuteScopedSqlForEmptyVisibleProjects() {
        CutoverDashboardCandidateMapper mapper = mock(CutoverDashboardCandidateMapper.class, CALLS_REAL_METHODS);
        CutoverDashboardCandidateQuery query = new CutoverDashboardCandidateQuery(1L, Set.of(), 0L, 500);

        assertThat(mapper.selectBatch(query)).isEqualTo(List.of());
        verify(mapper, never()).selectBatchScoped(query);
    }
}
