package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.CreateCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SaveCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.CutoverPlanCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverPlanApplicationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverPlanApplicationMySqlTest {
    @Resource JdbcTemplate jdbc;
    @Resource CutoverTaskMapper taskMapper;
    @Resource CutoverPlanApplicationService service;
    @Resource ControlledOwners owners;

    long tenantId;
    long taskId;

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
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        tenantId = 989_100_000_000L + suffix; taskId = 989_200_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId); owners.reset(taskId, 989_300_000_000L + suffix);
        CutoverTaskDO row = new CutoverTaskDO(); row.setId(taskId); row.setTenantId(tenantId);
        row.setProjectId(owners.projectId); row.setTaskNo("CUT-" + taskId); row.setTaskName("P4原子保存");
        row.setBackground("验证P4正向闭环"); row.setCutoverType("NETWORK_CUTOVER"); row.setNetworkMode("DUAL");
        row.setScheduledTime(LocalDateTime.of(2026, 9, 1, 10, 0)); row.setTaskOrigin("NEW_PLATFORM");
        row.setIntakeSourceType("SELF_CREATED"); row.setCurrentStage("P4"); row.setTaskStatus("PLAN_DRAFTING");
        row.setOwnerUserId(8L); row.setCustomerId(99L); row.setImplementationReadinessSnapshotId(7L);
        row.setImplementationReadinessSnapshotVersion(1L); row.setProjectScopeVersion(30L);
        row.setProjectContextSnapshot("{}"); row.setDeviceScopeWatermark("{}"); row.setCustomerContextSnapshot("{}");
        row.setReadinessContextSnapshot("{}"); row.setManualGrade("A"); row.setConfigurationRevisionId(401L);
        row.setConfigurationCode("CFG-1"); row.setConfigurationRevisionNo(1); row.setVersion(4);
        row.setCreator("8"); row.setUpdater("8"); assertEquals(1, taskMapper.insert(row));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cut_step WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_support_arrangement WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_plan_revision WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void createSaveReplayKeepsRootChildrenAndPlatformFactsAtomic() {
        CutoverPlanCommandResult created = service.createDraft(new CreateCutoverPlanDraftCommand(tenantId, 8L, taskId, 4, 30L,
                "ONLINE_TEMPLATE_STANDARD", null, null, "create-1", "corr-create-1"));
        assertEquals(created.planVersion(), count("SELECT version FROM cut_plan_revision WHERE tenant_id=?", tenantId));
        tools.jackson.databind.node.ObjectNode content = JsonUtils.getObjectMapper().createObjectNode();
        content.put("editMode", "ONLINE_TEMPLATE_STANDARD");
        tools.jackson.databind.node.ObjectNode overview = content.putObject("overview");
        overview.put("projectDescription", ""); overview.putArray("scheduleTable");
        overview.putNull("preTopologyFile"); overview.putNull("postTopologyFile");
        tools.jackson.databind.node.ObjectNode device = overview.putArray("deviceSummary").addObject();
        device.put("deviceId", 301L); device.put("serialNumber", "SN-1");
        device.put("projectAssignmentVersion", 9L); device.put("deviceTypeCode", "ROUTER");
        device.put("deviceTypeSourceVersion", "type-v1"); overview.putNull("networkConfigurationFile");
        tools.jackson.databind.node.ArrayNode steps = content.putArray("steps");
        steps.addObject().put("sectionCode", "OPERATION").put("stepNo", 1).put("content", "执行割接");
        steps.addObject().put("sectionCode", "ROLLBACK").put("stepNo", 1).put("content", "执行回退");
        content.putArray("riskMitigations");
        tools.jackson.databind.node.ObjectNode support = content.putArray("supportArrangements").addObject();
        support.putNull("arrangementId"); support.put("roleCode", "CUSTOMER"); support.put("personName", "客户经理");
        support.put("dutyDescription", "现场确认"); support.put("phone", "13800000000");
        support.put("arrivalTime", 1_788_192_000_000L);
        SaveCutoverPlanDraftCommand save = new SaveCutoverPlanDraftCommand(tenantId, 8L, taskId, 4, created.planVersion(),
                30L, content, "save-1", "corr-save-1");
        service.saveDraft(save); service.saveDraft(save);

        assertEquals(1, count("SELECT COUNT(*) FROM cut_plan_revision WHERE tenant_id=?", tenantId));
        assertEquals(2, count("SELECT COUNT(*) FROM cut_step WHERE tenant_id=?", tenantId));
        assertEquals(1, count("SELECT COUNT(*) FROM cut_cutover_support_arrangement WHERE tenant_id=?", tenantId));
        assertEquals(2, count("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND status='COMPLETED'", tenantId));
        assertEquals(2, count("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=?", tenantId));
        assertEquals(1, count("SELECT version FROM cut_plan_revision WHERE tenant_id=?", tenantId));
    }

    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    static String required(Map<String, String> env, String key) {
        String value = env.get(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required");
        return value;
    }

    static final class ControlledOwners implements CutoverProjectScopePort, CutoverPlanSourcePort, CutoverPlanFilePort {
        long taskId; long projectId; SourceFacts facts;
        void reset(long taskId, long projectId) {
            this.taskId = taskId; this.projectId = projectId;
            List<TemplateSectionSnapshot> sections = CutoverPlanRules.STANDARD_SECTIONS.stream().map(key ->
                    new TemplateSectionSnapshot(key, key, CutoverPlanRules.STANDARD_SECTIONS.indexOf(key) + 1,
                            List.of("NETWORK_CUTOVER"), List.of("A"), true)).toList();
            SourceSnapshot snapshot = new SourceSnapshot(1, taskId, 4, 100L, 2, "A", 200L, 3,
                    projectId, 6, 30L, List.of(new DeviceSnapshot(301L, "SN-1", 9L, "ROUTER", "type-v1")),
                    401L, "CFG-1", 1, sections, List.of());
            facts = new SourceFacts(snapshot, List.of());
        }
        @Override public ProjectScopeFact inspect(Long actorId, Long projectId, String action) { return new ProjectScopeFact(projectId, 30L, true); }
        @Override public ProjectScopeFact lockAndRevalidate(Long actorId, Long projectId, String action, long expected) { return new ProjectScopeFact(projectId, 30L, true); }
        @Override public java.util.Set<Long> resolveAllCurrent(Long actorId, String action) { return java.util.Set.of(projectId); }
        @Override public SourceFacts inspect(Long tenantId, Long actorId, Long taskId) { return facts; }
        @Override public SourceFacts lockAndRevalidate(Long tenantId, Long actorId, SourceFacts expected) { return facts; }
        @Override public FileFact inspect(Long tenantId, Long actorId, Long projectId, FileHandle handle) { throw new AssertionError(); }
        @Override public FileFact lockAndRevalidate(Long tenantId, Long actorId, Long projectId, FileHandle handle) { throw new AssertionError(); }
        @Override public FileFact downloadDraft(Long tenantId, Long actorId, Long projectId, Long planRevisionId) { throw new AssertionError(); }
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean ControlledOwners controlledOwners() { return new ControlledOwners(); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC); }
        @Bean CutoverPlanApplicationService service(CutoverTaskMapper taskMapper,
                                                    CutoverPlanRevisionMapper planMapper,
                                                    CutoverPlanStepMapper stepMapper,
                                                    CutoverSupportArrangementMapper supportMapper,
                                                    ControlledOwners owners,
                                                    PlatformCommandExecutionApi platform, Clock clock) {
            return new CutoverPlanApplicationService(taskMapper, planMapper, stepMapper, supportMapper,
                    owners, owners, owners, new CutoverPlanContentCodec(), platform, clock);
        }
    }
}
