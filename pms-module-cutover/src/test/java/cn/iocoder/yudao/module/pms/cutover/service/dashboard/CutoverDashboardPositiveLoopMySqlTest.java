package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.CutoverDashboardCandidateMapper;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CUT-01@V2 dashboard aggregation over real MyBatis/MySQL with controlled Owner facts. */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverDashboardPositiveLoopMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverDashboardPositiveLoopMySqlTest {
    private static final long ACTOR_ID = 9L;
    private static final long PROJECT_ID = 101L;

    @Resource JdbcTemplate jdbc;
    @Resource CutoverDashboardQueryService service;

    private long tenantId;
    private long baseId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Map<String, String> env = System.getenv();
        String database = env.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = env.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(env, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(env, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.cutover");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        long suffix = Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000L);
        tenantId = 991_700_000_000L + suffix;
        baseId = 991_710_000_000L + suffix * 10L;
        TenantContextHolder.setTenantId(tenantId);
        insertTask(1, "NEW_PLATFORM", "SELF_CREATED", "P2", "GRADE_CONFIRMING", "A");
        insertTask(2, "NEW_PLATFORM", "SELF_CREATED", "P5", "APPROVING", "A");
        insertTask(3, "NEW_PLATFORM", "SELF_CREATED", "P4", "PLAN_DRAFTING", "D");
        insertTask(4, "NEW_PLATFORM", "SELF_CREATED", "P6", "ARCHIVED", "A");
        insertTask(5, "NEW_PLATFORM", "SELF_CREATED", "P4", "PLAN_DRAFTING", "D");
        insertRejectedApproval(3);
        insertRejectedApproval(5);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cut_approval_instance WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_plan_revision WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void aggregatesOverlappingAuthorizedKpisWithoutMutatingCutFacts() {
        int tasksBefore = count("cut_task");
        int approvalsBefore = count("cut_approval_instance");
        int plansBefore = count("cut_plan_revision");

        CutoverDashboardKpiView result = service.inspect(tenantId, ACTOR_ID, allPermissions());

        assertEquals(3, result.todoCount());
        assertEquals(1, result.archivedCount());
        assertEquals(1, result.approvingCount());
        assertEquals(2, result.rejectedPendingModificationCount());
        assertEquals(Instant.parse("2026-09-02T01:00:00Z"), result.generatedAt().toInstant(ZoneOffset.UTC));
        assertEquals(tasksBefore, count("cut_task"));
        assertEquals(approvalsBefore, count("cut_approval_instance"));
        assertEquals(plansBefore, count("cut_plan_revision"));
    }

    private void insertTask(int offset, String origin, String intakeSource, String stage,
                            String status, String grade) {
        long id = baseId + offset;
        Long configurationRevisionId = "NEW_PLATFORM".equals(origin) ? baseId + 500 : null;
        String configurationCode = "NEW_PLATFORM".equals(origin) ? "CFG-DASH" : null;
        Integer configurationRevisionNo = "NEW_PLATFORM".equals(origin) ? 1 : null;
        jdbc.update("""
                INSERT INTO cut_task
                (id,tenant_id,project_id,task_no,task_name,task_origin,intake_source_type,current_stage,
                 task_status,owner_user_id,customer_id,background,cutover_type,manual_grade,
                 configuration_revision_id,configuration_code,configuration_revision_no,
                 implementation_readiness_snapshot_id,implementation_readiness_snapshot_version,
                 project_scope_version,project_context_snapshot,device_scope_watermark,
                 customer_context_snapshot,readiness_context_snapshot,
                 version,creator,create_time,updater,update_time,deleted)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,7,
                        JSON_OBJECT('projectId',?),JSON_OBJECT('devices',JSON_ARRAY()),
                        JSON_OBJECT('customerId',?),JSON_OBJECT('readinessSnapshotId',?),
                        0,'dashboard-test',NOW(3),'dashboard-test',NOW(3),b'0')
                """, id, tenantId, PROJECT_ID, "CUT-DASH-" + offset, "割接任务" + offset,
                origin, intakeSource, stage, status, ACTOR_ID, baseId + 600,
                "受控正向闭环", "STANDARD", grade,
                configurationRevisionId, configurationCode, configurationRevisionNo,
                baseId + 700, PROJECT_ID, baseId + 600, baseId + 700);
    }

    private void insertRejectedApproval(int taskOffset) {
        long taskId = baseId + taskOffset;
        long planId = baseId + 100 + taskOffset;
        long approvalId = baseId + 200 + taskOffset;
        jdbc.update("""
                INSERT INTO cut_plan_revision
                (id,tenant_id,cutover_task_id,revision_no,origin_code,edit_mode_code,grade_code,
                 assessment_id,assessment_version,configuration_revision_id,configuration_code,
                 configuration_revision_no,template_section_snapshot,source_snapshot,content_snapshot,
                 status_code,current_marker,submitted_by,submitted_at,approval_instance_id,approval_version,
                 version,creator,create_time,updater,update_time,deleted)
                VALUES (?,?,?,1,'NEW_PLATFORM','ONLINE_TEMPLATE_SIMPLE_D','D',?,1,?,'CFG-DASH',1,
                        JSON_ARRAY(),JSON_OBJECT(),JSON_OBJECT('editMode','ONLINE_TEMPLATE_SIMPLE_D','steps',JSON_ARRAY()),
                        'SUBMITTED',1,?,NOW(3),?,0,0,'dashboard-test',NOW(3),'dashboard-test',NOW(3),b'0')
                """, planId, tenantId, taskId, baseId + 300 + taskOffset,
                baseId + 400 + taskOffset, ACTOR_ID, approvalId);
        jdbc.update("""
                INSERT INTO cut_approval_instance
                (id,tenant_id,task_id,project_id,plan_revision_id,plan_revision_no,assessment_id,
                 assessment_version,grade_code,initiator_user_id,initiator_project_scope_version,
                 source_snapshot_version,source_snapshot,route_snapshot,status_code,decision_at,rejection_reason,
                 version,creator,create_time,updater,update_time,deleted)
                VALUES (?,?,?,?,?,1,?,1,'D',?,7,1,JSON_OBJECT(),JSON_OBJECT(),'REJECTED',NOW(3),
                        '方案需修改',0,'dashboard-test',NOW(3),'dashboard-test',NOW(3),b'0')
                """, approvalId, tenantId, taskId, PROJECT_ID, planId,
                baseId + 300 + taskOffset, ACTOR_ID);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?", Integer.class, tenantId);
    }

    private static PermissionFacts allPermissions() {
        return new PermissionFacts(true, true, true, true, true, true, true,
                true, true, true, true, true, true, true);
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-02T01:00:00Z"), ZoneOffset.UTC);
        }

        @Bean CutoverProjectScopePort projectScopePort() {
            return new CutoverProjectScopePort() {
                @Override public ProjectScopeFact inspect(Long actorId, Long projectId, String action) {
                    return new ProjectScopeFact(projectId, 7L, true);
                }
                @Override public ProjectScopeFact lockAndRevalidate(Long actorId, Long projectId, String action,
                                                                     long expectedProjectScopeVersion) {
                    return new ProjectScopeFact(projectId, 7L, true);
                }
                @Override public Set<Long> resolveAllCurrent(Long actorId, String action) {
                    return Set.of(PROJECT_ID);
                }
            };
        }

        @Bean CutoverDashboardActionFactPort actionFactPort() {
            return query -> query.candidates().stream().map(candidate -> new CutoverDashboardActionFacts(
                    candidate.taskId(), switch (candidate.currentStage()) {
                        case "P2" -> ActionFacts.p2p3("DRAFT", true, null, true, true);
                        case "P4" -> ActionFacts.p4("SUBMITTED", 1, "REJECTED",
                                candidate.taskId() % 10 != 5, true, true, true);
                        case "P5" -> ActionFacts.p5("PENDING", "PENDING", null, ACTOR_ID, true);
                        default -> throw new IllegalStateException("unexpected controlled dashboard stage");
                    })).toList();
        }

        @Bean CutoverDashboardQueryService service(CutoverDashboardCandidateMapper mapper,
                                                    CutoverProjectScopePort scope,
                                                    CutoverDashboardActionFactPort facts, Clock clock) {
            return new CutoverDashboardQueryService(mapper, scope, facts, clock);
        }
    }
}
