package cn.iocoder.yudao.module.pms.cutover.service.taskv2;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverAssessmentMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SaveCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.command.SubmitCutoverAssessmentCommand;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceProductTypePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverAssessmentCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.result.CutoverTaskCommandResult;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CutoverTaskPositiveLoopMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CutoverTaskPositiveLoopMySqlTest {

    private static final long ACTOR_ID = 8L;
    private static final long PROJECT_ID = 100L;
    private static final long DEVICE_ID = 400L;

    @Resource JdbcTemplate jdbc;
    @Resource CutoverConfigurationRevisionMapper configurationMapper;
    @Resource CutoverTaskApplicationService service;

    private long tenantId;

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
        tenantId = 996_100_000_000L + Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000L);
        TenantContextHolder.setTenantId(tenantId);
        CutoverConfigurationRevisionDO configuration = new CutoverConfigurationRevisionDO();
        configuration.setId(996_200_000_000L + Math.floorMod(tenantId, 1_000_000L));
        configuration.setTenantId(tenantId);
        configuration.setConfigurationCode("CUTOVER-V1");
        configuration.setConfigurationName("CUT-002受控正向配置");
        configuration.setRevisionNo(1);
        configuration.setStatusCode("PUBLISHED");
        configuration.setEffectiveFrom(LocalDateTime.of(2026, 8, 1, 0, 0));
        configuration.setDictionarySnapshot("{}");
        configuration.setDimensionDefinitionSnapshot("[]");
        configuration.setPlanTemplateSectionSnapshot("[]");
        configuration.setValidationResultSnapshot("[]");
        configuration.setPublishedBy(ACTOR_ID);
        configuration.setPublishedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        configuration.setVersion(0);
        configuration.setCreator(String.valueOf(ACTOR_ID));
        configuration.setUpdater(String.valueOf(ACTOR_ID));
        assertEquals(1, configurationMapper.insert(configuration));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM cut_assessment WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task_stage_history WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task_device_scope WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_operation_audit WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM plt_idempotency_record WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_task WHERE tenant_id=?", tenantId);
        jdbc.update("DELETE FROM cut_cutover_configuration_revision WHERE tenant_id=?", tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void completesSelfCreatedP1P2AssessmentAIntoP3WithRealTransactionAndMyBatis() {
        CutoverTaskCommandResult created = service.create(createCommand("create-a"));
        CutoverAssessmentCommandResult saved = service.saveAssessment(saveCommand(
                created.taskId(), created.version(), "A"));
        SubmitCutoverAssessmentCommand submit = submitCommand(created.taskId(), saved.taskVersion(),
                saved.assessmentRowVersion(), "submit-a");

        CutoverTaskCommandResult submitted = service.submitAssessment(submit);
        CutoverTaskCommandResult replayed = service.submitAssessment(submit);

        assertEquals("P3", submitted.currentStage());
        assertEquals("SURVEYING", submitted.taskStatus());
        assertTrue(replayed.replayed());
        assertEquals(2, number("SELECT version FROM cut_task WHERE tenant_id=? AND id=?", tenantId, created.taskId()));
        assertEquals("A", text("SELECT manual_grade FROM cut_task WHERE tenant_id=? AND id=?", tenantId, created.taskId()));
        assertEquals("SUBMITTED", text("SELECT assessment_status FROM cut_assessment WHERE tenant_id=? AND cutover_task_id=?", tenantId, created.taskId()));
        assertFalse(bool("SELECT simple_flow FROM cut_assessment WHERE tenant_id=? AND cutover_task_id=?", tenantId, created.taskId()));
        assertEquals(2, number("SELECT COUNT(*) FROM cut_task_stage_history WHERE tenant_id=? AND cutover_task_id=?", tenantId, created.taskId()));
        assertEquals(2, number("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND status='COMPLETED'", tenantId));
        assertEquals(2, number("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=?", tenantId));
    }

    @Test
    void completesSelfCreatedP1P2AssessmentDIntoP4WithRealTransactionAndMyBatis() {
        CutoverTaskCommandResult created = service.create(createCommand("create-d"));
        CutoverAssessmentCommandResult saved = service.saveAssessment(saveCommand(
                created.taskId(), created.version(), "D"));

        CutoverTaskCommandResult submitted = service.submitAssessment(submitCommand(
                created.taskId(), saved.taskVersion(), saved.assessmentRowVersion(), "submit-d"));

        assertEquals("P4", submitted.currentStage());
        assertEquals("PLAN_DRAFTING", submitted.taskStatus());
        assertEquals("D", text("SELECT manual_grade FROM cut_task WHERE tenant_id=? AND id=?", tenantId, created.taskId()));
        assertTrue(bool("SELECT simple_flow FROM cut_assessment WHERE tenant_id=? AND cutover_task_id=?", tenantId, created.taskId()));
        assertEquals("P4", text("SELECT to_stage FROM cut_task_stage_history WHERE tenant_id=? AND cutover_task_id=? ORDER BY sequence_no DESC LIMIT 1", tenantId, created.taskId()));
        assertEquals("ROUTER", text("SELECT device_type_code_snapshot FROM cut_task_device_scope WHERE tenant_id=? AND cutover_task_id=?", tenantId, created.taskId()));
    }

    private CreateCutoverTaskCommand createCommand(String key) {
        return new CreateCutoverTaskCommand(tenantId, ACTOR_ID, key, "corr-" + key, "SELF_CREATED", PROJECT_ID,
                List.of("SN-400"), "CUTOVER-V1", "核心网割接", "计划内设备割接", "配置变更", "普通双机",
                LocalDateTime.of(2026, 9, 1, 1, 0), null, null, null,
                new CreateCutoverTaskCommand.ExpectedCreateContext(projectFact(tenantId), List.of(deviceFact()),
                        customerFact(), readinessFact()));
    }

    private SaveCutoverAssessmentCommand saveCommand(Long taskId, Integer taskVersion, String grade) {
        return new SaveCutoverAssessmentCommand(tenantId, ACTOR_ID, taskId, taskVersion, 0,
                new CutoverAssessmentAnswers("HIGH", "MEDIUM", "LOW", true), grade, "corr-save-" + grade);
    }

    private SubmitCutoverAssessmentCommand submitCommand(Long taskId, Integer taskVersion,
                                                           Integer assessmentVersion, String key) {
        return new SubmitCutoverAssessmentCommand(tenantId, ACTOR_ID, taskId, taskVersion, assessmentVersion,
                key, "corr-" + key);
    }

    private int number(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private boolean bool(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }

    private static CutoverProjectContextPort.ProjectContextFact projectFact(long tenantId) {
        return new CutoverProjectContextPort.ProjectContextFact(tenantId, PROJECT_ID, 3,
                "PROJ-100", "核心网割接项目", 200L, "CUS-200", "示例客户",
                300L, "OFFICE-300", "一号办事处", 7L);
    }

    private static CutoverDeviceScopePort.DeviceFact deviceFact() {
        return new CutoverDeviceScopePort.DeviceFact(DEVICE_ID, "SN-400", PROJECT_ID, 9L);
    }

    private static CutoverCustomerLevelPort.CustomerLevelFact customerFact() {
        return new CutoverCustomerLevelPort.CustomerLevelFact("AVAILABLE", 200L, "CUS-200", "示例客户",
                500L, "LEVEL_1", 2L, LocalDateTime.of(2026, 8, 1, 0, 0), null);
    }

    private static CutoverReadinessPort.ReadinessFact readinessFact() {
        return new CutoverReadinessPort.ReadinessFact(600L, 4L, "READY", PROJECT_ID,
                List.of(DEVICE_ID), "watermark-1", List.of());
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    @SpringBootConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan({"cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2",
            "cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-08-31T01:00:00Z"), ZoneOffset.UTC); }
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
        @Bean CutoverProjectContextPort projectContextPort() {
            return new CutoverProjectContextPort() {
                @Override public ProjectContextFact inspect(Long tenantId, Long projectId,
                                                             long expectedProjectScopeVersion) {
                    return projectFact(tenantId);
                }
                @Override public ProjectContextFact lockAndRevalidate(ProjectContextFact expected) {
                    return expected;
                }
            };
        }
        @Bean CutoverDeviceScopePort deviceScopePort() {
            return new CutoverDeviceScopePort() {
                @Override public List<DeviceFact> resolveBySerials(List<String> serialNumbers) {
                    return List.of(deviceFact());
                }
                @Override public List<DeviceFact> lockAndRevalidate(Long projectId, List<DeviceFact> expectedDevices) {
                    return List.copyOf(expectedDevices);
                }
            };
        }
        @Bean CutoverDeviceProductTypePort productTypePort() {
            return (actorId, deviceIds) -> List.of(new CutoverDeviceProductTypePort.ProductTypeFact(
                    DEVICE_ID, "ROUTER", true, "pt-v1", "RESOLVED", "FRESH",
                    LocalDateTime.of(2026, 8, 31, 0, 0), false));
        }
        @Bean CutoverCustomerLevelPort customerLevelPort() {
            return new CutoverCustomerLevelPort() {
                @Override public CustomerLevelFact inspect(Long customerId) { return customerFact(); }
                @Override public CustomerLevelFact lockAndRevalidate(CustomerLevelFact expected) { return expected; }
            };
        }
        @Bean CutoverReadinessPort readinessPort() {
            return new CutoverReadinessPort() {
                @Override public ReadinessFact inspect(Long projectId, List<Long> deviceIds) { return readinessFact(); }
                @Override public ReadinessFact lockAndRevalidate(ReadinessFact expected) { return expected; }
            };
        }
        @Bean CutoverTaskApplicationService service(CutoverTaskMapper taskMapper,
                                                     CutoverTaskDeviceScopeMapper deviceMapper,
                                                     CutoverTaskStageHistoryMapper historyMapper,
                                                     CutoverAssessmentMapper assessmentMapper,
                                                     CutoverConfigurationRevisionMapper configurationMapper,
                                                     CutoverProjectScopePort projectScopePort,
                                                     CutoverProjectContextPort projectContextPort,
                                                     CutoverDeviceScopePort deviceScopePort,
                                                     CutoverDeviceProductTypePort productTypePort,
                                                     CutoverCustomerLevelPort customerLevelPort,
                                                     CutoverReadinessPort readinessPort,
                                                     PlatformCommandExecutionApi platform, Clock clock) {
            return new CutoverTaskApplicationService(taskMapper, deviceMapper, historyMapper, assessmentMapper,
                    configurationMapper, projectScopePort, projectContextPort, deviceScopePort, productTypePort,
                    customerLevelPort, readinessPort, platform, clock);
        }
    }
}
