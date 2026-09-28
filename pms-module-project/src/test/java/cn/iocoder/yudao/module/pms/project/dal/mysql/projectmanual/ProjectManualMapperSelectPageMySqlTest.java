package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.VisibleProjectPageQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 MySQL 验证 XML selectVisiblePage 语义（状态阶段/时间左闭右开/合同/归属/当事方）。 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ProjectManualMySqlTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectManualMapperSelectPageMySqlTest {

    private static final long TENANT = 0L;
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.now();

    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;

    private final Set<Long> fixtureProjectIds = new HashSet<>();

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.project");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @AfterEach
    void cleanupFixture() {
        for (Long id : fixtureProjectIds) {
            jdbcTemplate.update("DELETE FROM proj_project_party WHERE tenant_id=? AND project_id=?", TENANT, id);
            jdbcTemplate.update("DELETE FROM proj_project WHERE tenant_id=? AND id=?", TENANT, id);
        }
        fixtureProjectIds.clear();
    }

    @Test
    void stageStatusFiltersCurrentStageWithActiveLifecycle() {
        long stageProject = insertProject("S1", "ACTIVE", "S1-ACTIVE");
        long closedAtS1 = insertProject("S1", "NORMAL_CLOSED", "CLOSED-AT-S1");

        List<Long> ids = selectPageIds(builder().status("S1").build());

        assertTrue(ids.contains(stageProject));
        assertFalse(ids.contains(closedAtS1));
    }

    @Test
    void closureStatusMatchesLifecycleWithoutStage() {
        long activeS1 = insertProject("S1", "ACTIVE", "ACTIVE-S1");
        long normalClosed = insertProject("S1", "NORMAL_CLOSED", "NORMAL-CLOSED");

        List<Long> ids = selectPageIds(builder().status("NORMAL_CLOSED").build());

        assertTrue(ids.contains(normalClosed));
        assertFalse(ids.contains(activeS1));
    }

    @Test
    void blankStatusKeepsActiveAndClosedRows() {
        long active = insertProject("S2", "ACTIVE", "BLANK-ACTIVE");
        long closed = insertProject("S2", "NORMAL_CLOSED", "BLANK-CLOSED");

        List<Long> ids = selectPageIds(builder().build());

        assertTrue(ids.contains(active));
        assertTrue(ids.contains(closed));
    }

    @Test
    void timeRangesUseLeftClosedRightOpen() {
        long created = insertProject("S0", "ACTIVE", "TIME");
        jdbcTemplate.update("UPDATE proj_project SET create_time=? WHERE tenant_id=? AND id=?",
                LocalDateTime.of(2026, 9, 1, 0, 0), TENANT, created);
        jdbcTemplate.update("UPDATE proj_project SET project_close_time=? WHERE tenant_id=? AND id=?",
                LocalDateTime.of(2026, 9, 10, 0, 0), TENANT, created);

        // [9-01 00:00, 9-02 00:00) 命中创建时间
        assertTrue(selectPageIds(builder()
                .createTimeStart(LocalDateTime.of(2026, 9, 1, 0, 0))
                .createTimeEnd(LocalDateTime.of(2026, 9, 2, 0, 0)).build()).contains(created));
        // 右开：9-01 00:00 作为 endExclusive 不命中
        assertFalse(selectPageIds(builder()
                .createTimeEnd(LocalDateTime.of(2026, 9, 1, 0, 0)).build()).contains(created));
        // 闭环时间右开边界
        assertTrue(selectPageIds(builder()
                .closeTimeStart(LocalDateTime.of(2026, 9, 10, 0, 0))
                .closeTimeEnd(LocalDateTime.of(2026, 9, 11, 0, 0)).build()).contains(created));
        assertFalse(selectPageIds(builder()
                .closeTimeEnd(LocalDateTime.of(2026, 9, 10, 0, 0)).build()).contains(created));
    }

    @Test
    void contractAndOwnershipFiltersMatch() {
        long project = insertProject("S0", "ACTIVE", "ATTR");
        jdbcTemplate.update("UPDATE proj_project SET contract_no=?, company_id=?, department_id=?, "
                        + "major_project_level=? WHERE tenant_id=? AND id=?",
                "HT-XML-2026-001", 88001L, 88002L, "OFFICE_MAJOR", TENANT, project);

        assertTrue(selectPageIds(builder().contractNoKeyword("XML-2026").build()).contains(project));
        assertTrue(selectPageIds(builder().companyId(88001L).build()).contains(project));
        assertTrue(selectPageIds(builder().departmentId(88002L).build()).contains(project));
        assertTrue(selectPageIds(builder().majorProjectLevel("OFFICE_MAJOR").build()).contains(project));
        assertFalse(selectPageIds(builder().contractNoKeyword("XML-NONE").build()).contains(project));
        assertFalse(selectPageIds(builder().companyId(99999L).build()).contains(project));
    }

    @Test
    void agentServiceProviderKeywordMatchesCurrentPartyOnly() {
        long project = insertProject("S0", "ACTIVE", "PARTY");
        jdbcTemplate.update("INSERT INTO proj_project_party "
                        + "(id, tenant_id, project_id, party_role, party_code, party_name, status, "
                        + "source_system, source_table, source_record_key, creator, updater, version, deleted) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0,b'0')",
                project + 1, TENANT, project, "AGENT", "AG-XML-001", "IT-XML-代理商科技", "ACTIVE",
                "IT-XML-TEST", "proj_project_party", String.valueOf(project), "it", "it");
        // 已闭合历史区间不参与匹配
        jdbcTemplate.update("INSERT INTO proj_project_party "
                        + "(id, tenant_id, project_id, party_role, party_code, party_name, status, effective_from, effective_to, "
                        + "source_system, source_table, source_record_key, creator, updater, version, deleted) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,b'0')",
                project + 2, TENANT, project, "SERVICE_PROVIDER", "SP-XML-OLD", "IT-XML-旧服务商", "ACTIVE",
                EFFECTIVE_AT.minusDays(10), EFFECTIVE_AT.minusDays(1),
                "IT-XML-TEST", "proj_project_party", String.valueOf(project), "it", "it");

        assertTrue(selectPageIds(builder().agentServiceProviderKeyword("代理商科技").build()).contains(project));
        assertTrue(selectPageIds(builder().agentServiceProviderKeyword("AG-XML-001").build()).contains(project));
        assertFalse(selectPageIds(builder().agentServiceProviderKeyword("旧服务商").build()).contains(project));
        assertFalse(selectPageIds(builder().agentServiceProviderKeyword("XML-NONE").build()).contains(project));
    }

    @Test
    void emptyPermissionSetReturnsEmptyPageWithoutQuery() {
        PageResult<ProjectMasterDO> result = projectMasterMapper.selectPage(
                builder().visibleProjectIds(Set.of()).build());
        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    private VisibleProjectPageQuery.VisibleProjectPageQueryBuilder builder() {
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(1);
        pageParam.setPageSize(100);
        return VisibleProjectPageQuery.builder()
                .tenantId(TENANT)
                .visibleProjectIds(new HashSet<>(fixtureProjectIds))
                .pageParam(pageParam)
                .effectiveAt(EFFECTIVE_AT);
    }

    private List<Long> selectPageIds(VisibleProjectPageQuery query) {
        return projectMasterMapper.selectPage(query).getList()
                .stream().map(ProjectMasterDO::getId).toList();
    }

    private long insertProject(String currentStage, String lifecycleStatus, String codeSuffix) {
        long seed = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        long id = 976_000_000_000L + seed * 100L;
        jdbcTemplate.update("INSERT INTO proj_project "
                        + "(id, project_code, code_root_id, project_sequence, project_name, root_id, tree_path, "
                        + "tree_depth, tree_sort, status, lifecycle_status, current_stage, assignment_status, "
                        + "task_tree_version, task_progress_version, version, tenant_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?, 'ACTIVE', ?, ?, 'UNASSIGNED', 0, 0, 0, ?)",
                id, "IT-XML-" + codeSuffix + "-" + id, id, 0, "IT-XML " + codeSuffix, id, "/", 0, 0,
                lifecycleStatus, currentStage, TENANT);
        fixtureProjectIds.add(id);
        return id;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }
}
