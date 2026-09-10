package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRuntimeGraphMapperTest {
    @Test
    void boundQueriesKeepScopeAndSeparatePreviewFromLocks() throws Exception {
        var configuration = new Configuration();
        String resource = "mapper/runtimegraph/ProjectRuntimeGraphMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        for (String method : new String[]{"selectStages", "selectTasks", "selectGates", "selectStagesForUpdate",
                "selectTasksForUpdate", "selectGatesForUpdate", "selectContracts", "selectTransitions"}) {
            var statement = configuration.getMappedStatement(ProjectRuntimeGraphMapper.class.getName() + "." + method);
            var bound = statement.getBoundSql(Map.of("query", new ProjectRuntimeGraphQuery(7L, 9L)));
            String sql = bound.getSql().replaceAll("\\s+", " ").trim();
            assertTrue(sql.contains("tenant_id = ? AND project_id = ? AND deleted = FALSE"), method);
            assertEquals("query.tenantId", bound.getParameterMappings().getFirst().getProperty());
            assertEquals("query.projectId", bound.getParameterMappings().get(1).getProperty());
            assertEquals(method.endsWith("ForUpdate"), sql.endsWith("FOR UPDATE"), method);
            assertFalse(sql.contains("sort_order"), method);
            if (method.equals("selectContracts")) {
                assertTrue(sql.contains("effective_from <= CURRENT_TIMESTAMP AND effective_to IS NULL"));
            }
        }
    }
}
