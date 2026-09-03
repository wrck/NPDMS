package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverApprovedContactVersionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanHistoryQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanReplacementUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanSuccessorQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanVersionUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverSupportContactUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskSourceInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskApprovalTransitionUpdate;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CutoverPlanMapperContractTest {
    @Test
    void parsesEveryScenarioQueryAndUpdateBinding() throws IOException {
        Configuration configuration = configuration();
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "selectCurrentForUpdate",
                new CutoverPlanRevisionQuery(1L, 2L, 3L));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "selectByIdForUpdate",
                new CutoverPlanRevisionQuery(1L, 2L, 3L));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "selectListLegacyByTask",
                new CutoverPlanRevisionQuery(1L, 2L, 3L));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "selectListDirectSuccessors",
                new CutoverPlanSuccessorQuery(1L, 2L, 3L));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "selectMaxRevisionNo",
                new CutoverPlanHistoryQuery(1L, 2L));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "advanceDraftVersionIfMatch",
                new CutoverPlanVersionUpdate(1L, 3L, 0, 1));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "advanceApprovedVersionIfMatch",
                new CutoverApprovedContactVersionUpdate(1L, 3L, 0, 1, "9",
                        LocalDateTime.parse("2026-09-01T09:00:00")));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "replaceSubmittedIfMatch",
                new CutoverPlanReplacementUpdate(1L, 3L, 0, 1, "9",
                        LocalDateTime.parse("2026-09-01T09:00:00")));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "replaceDraftIfMatch",
                new CutoverPlanDraftUpdate(1L, 3L, 0, 1, "ONLINE_TEMPLATE_STANDARD", "{}",
                        null, null, null, null, null, null, null, "9",
                        LocalDateTime.parse("2026-09-01T09:00:00")));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "submitDraftIfMatch",
                new CutoverPlanSubmitUpdate(1L, 3L, 0, 1, 9L,
                        LocalDateTime.parse("2026-09-01T09:00:00"), 10L, 0));
        assertBindings(configuration, CutoverPlanRevisionMapper.class, "invalidateSubmittedIfMatch",
                new CutoverPlanInvalidationUpdate(1L, 3L, 1, 2, 0, 1, 9L,
                        LocalDateTime.parse("2026-09-01T10:00:00"), "SOURCE_FACT_INVALIDATED"));
        assertBindings(configuration, CutoverTaskMapper.class, "submitPlanIfMatch",
                new CutoverTaskPlanSubmitUpdate(1L, 2L, 3));
        assertBindings(configuration, CutoverTaskMapper.class, "returnToPlanForSourceInvalidation",
                new CutoverTaskSourceInvalidationUpdate(1L, 2L, 4));
        assertBindings(configuration, CutoverTaskMapper.class, "transitionFromApprovalIfMatch",
                new CutoverTaskApprovalTransitionUpdate(1L, 2L, 4, "P6", "CLOSURE_IN_PROGRESS"));
        assertBindings(configuration, CutoverTaskMapper.class, "selectMaxStageHistorySequence",
                new CutoverTaskRowQuery(1L, 2L));
        assertBindings(configuration, CutoverPlanStepMapper.class, "selectListByPlanForUpdate",
                new CutoverPlanChildrenQuery(1L, 3L));
        assertBindings(configuration, CutoverSupportArrangementMapper.class, "deleteDraftRows",
                new CutoverPlanChildrenQuery(1L, 3L));
        assertBindings(configuration, CutoverSupportArrangementMapper.class, "updateApprovedContactIfMatch",
                new CutoverSupportContactUpdate(1L, 4L, 3L, 0, "name", "phone",
                        LocalDateTime.parse("2026-09-01T10:00:00"), "9", LocalDateTime.parse("2026-09-01T09:00:00")));
    }

    @Test
    void keepsTenantDeleteLocksAndStableOrderingInXml() throws IOException {
        String root = Files.readString(Path.of("src/main/resources/mapper/planv2/CutoverPlanRevisionMapper.xml"));
        String step = Files.readString(Path.of("src/main/resources/mapper/planv2/CutoverPlanStepMapper.xml"));
        String support = Files.readString(Path.of("src/main/resources/mapper/planv2/CutoverSupportArrangementMapper.xml"));
        assertThat(root).contains("tenant_id = #{query.tenantId}", "deleted = b'0'", "FOR UPDATE",
                "ORDER BY revision_no ASC, id ASC", "origin_code = 'LEGACY_FORWARD'",
                "current_marker IS NULL").doesNotContain("${");
        assertThat(step).contains("tenant_id = #{query.tenantId}", "FOR UPDATE", "step_no ASC, id ASC")
                .doesNotContain("${");
        assertThat(support).contains("tenant_id = #{query.tenantId}", "FOR UPDATE", "role_code")
                .doesNotContain("${");
    }

    private static Configuration configuration() throws IOException {
        Configuration configuration = new Configuration();
        parse(configuration, "CutoverPlanRevisionMapper.xml");
        parse(configuration, "CutoverPlanStepMapper.xml");
        parse(configuration, "CutoverSupportArrangementMapper.xml");
        parse(configuration, "../taskv2/CutoverTaskMapper.xml");
        return configuration;
    }

    private static void parse(Configuration configuration, String file) throws IOException {
        Path path = Path.of("src/main/resources/mapper/planv2", file);
        try (InputStream input = Files.newInputStream(path)) {
            new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
        }
    }

    private static void assertBindings(Configuration configuration, Class<?> mapper, String method, Object query) {
        Map<String, Object> parameters = Map.of("query", query);
        BoundSql boundSql = configuration.getMappedStatement(mapper.getName() + "." + method).getBoundSql(parameters);
        assertThat(boundSql.getParameterMappings()).isNotEmpty();
        boundSql.getParameterMappings().forEach(mapping ->
                configuration.newMetaObject(parameters).getValue(mapping.getProperty()));
    }
}
