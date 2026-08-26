package cn.iocoder.yudao.module.pms.engineering.constructionplan;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.DurationChangeProperties;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateDurationChangeCommand;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateInitialDurationCommand;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.PatchDurationChangeCommand;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.patch.DurationChangePatch;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ConstructionPlanMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConstructionPlanMySqlIntegrationTest {

    @Resource ConstructionPlanApplicationService service;
    @Resource DurationChangeApplicationService changeService;
    @Resource JdbcTemplate jdbcTemplate;
    @Resource PermissionApi permissionApi;
    @Resource ProjectScopeApi projectScopeApi;
    @Resource ProjectParticipantFactApi participantFactApi;
    @Resource RevisionInsertFault revisionInsertFault;

    private long projectId;
    private String keyPrefix;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "npdms");
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "13306");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
                + "&characterEncoding=UTF-8&nullCatalogMeansCurrent=true");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.engineering");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        projectId = 979_000_000_000L
                + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        keyPrefix = "fsol001-task9-it-" + projectId;
        revisionInsertFault.enabled = false;
        reset(permissionApi, projectScopeApi, participantFactApi);
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(projectId, 7L, Set.of(projectId), Set.of()));
        when(participantFactApi.lockAndRevalidate(any())).thenReturn(new ProjectParticipantFact(
                projectId, 9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY",
                "ACTIVE", "S1", 3, 3L));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("UPDATE sol_construction_plan SET current_duration_revision_id=NULL, "
                + "pending_change_id=NULL, plan_recalculation_source_revision_id=NULL "
                + "WHERE tenant_id=0 AND project_id=?", projectId);
        jdbcTemplate.update("DELETE c FROM sol_construction_plan_change c "
                + "JOIN sol_construction_plan p ON p.tenant_id=c.tenant_id AND p.id=c.plan_id "
                + "WHERE p.tenant_id=0 AND p.project_id=?", projectId);
        jdbcTemplate.update("DELETE r FROM sol_construction_plan_revision r "
                + "JOIN sol_construction_plan p ON p.tenant_id=r.tenant_id AND p.id=r.plan_id "
                + "WHERE p.tenant_id=0 AND p.project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM sol_construction_plan WHERE tenant_id=0 AND project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=0 "
                + "AND correlation_id LIKE ?", keyPrefix + "%");
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=0 "
                + "AND scope_code IN ('POST:/api/v1/pms/construction-plans', "
                + "'POST:/api/v1/pms/construction-plans/{id}/changes') AND actor_id=9 "
                + "AND idempotency_key LIKE ?", keyPrefix + "%");
        TenantContextHolder.clear();
    }

    @Test
    void successAndReplayKeepOneBaselineIdempotencyAndAuditFact() {
        String key = keyPrefix + "-success";
        var first = service.createInitial(command(key, "a".repeat(64)), actor(key));
        var replay = service.createInitial(command(key, "a".repeat(64)), actor(key + "-replay"));

        assertEquals(first.getPlanId(), replay.getPlanId());
        assertEquals(1L, count("sol_construction_plan", "project_id", projectId));
        assertEquals(1L, count("sol_construction_plan_revision", "plan_id", first.getPlanId()));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='POST:/api/v1/pms/construction-plans' "
                + "AND actor_id=9 AND idempotency_key=? AND status='COMPLETED'", Long.class, key));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND operation_code='CONSTRUCTION_PLAN_INITIAL_DURATION_CREATE' "
                + "AND aggregate_key=? AND result_code='SUCCESS'", Long.class,
                String.valueOf(first.getPlanId())));
    }

    @Test
    void differentPayloadForSameKeyIsRejectedWithoutSecondBusinessFact() {
        String key = keyPrefix + "-conflict";
        var first = service.createInitial(command(key, "a".repeat(64)), actor(key));

        assertThrows(ServiceException.class,
                () -> service.createInitial(command(key, "b".repeat(64)), actor(key + "-other")));

        assertEquals(1L, count("sol_construction_plan", "project_id", projectId));
        assertEquals(1L, count("sol_construction_plan_revision", "plan_id", first.getPlanId()));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND correlation_id=? AND result_code='REJECTED'", Long.class,
                key + "-other"));
    }

    @Test
    void revisionFailureRollsBackPlanIdempotencyAndSuccessAudit() {
        String key = keyPrefix + "-fault";
        revisionInsertFault.enabled = true;

        assertThrows(IllegalStateException.class,
                () -> service.createInitial(command(key, "a".repeat(64)), actor(key)));

        assertEquals(0L, count("sol_construction_plan", "project_id", projectId));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code='POST:/api/v1/pms/construction-plans' "
                + "AND actor_id=9 AND idempotency_key=?", Long.class, key));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND correlation_id=? AND result_code='REJECTED'", Long.class, key));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND correlation_id=? AND result_code='SUCCESS'", Long.class, key));
    }

    @Test
    void changeDraftReplayAndConflictKeepOneCandidateAndOneChange() {
        var plan = service.createInitial(command(keyPrefix + "-baseline", "a".repeat(64)),
                actor(keyPrefix + "-baseline"));
        String key = keyPrefix + "-draft";

        var first = changeService.createDraft(changeCommand(plan.getPlanId(), plan.getPlanVersion(), key,
                "b".repeat(64)), actor(key));
        var replay = changeService.createDraft(changeCommand(plan.getPlanId(), plan.getPlanVersion(), key,
                "b".repeat(64)), actor(key + "-replay"));

        assertEquals(first.getChangeId(), replay.getChangeId());
        assertThrows(ServiceException.class, () -> changeService.createDraft(
                changeCommand(plan.getPlanId(), plan.getPlanVersion(), key, "c".repeat(64)),
                actor(key + "-conflict")));
        assertEquals(2L, count("sol_construction_plan_revision", "plan_id", plan.getPlanId()));
        assertEquals(1L, count("sol_construction_plan_change", "plan_id", plan.getPlanId()));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sol_construction_plan "
                + "WHERE tenant_id=0 AND id=? AND pending_change_id IS NOT NULL", Long.class,
                plan.getPlanId()));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code="
                + "'POST:/api/v1/pms/construction-plans/{id}/changes' AND actor_id=9 "
                + "AND idempotency_key=? AND status='COMPLETED'", Long.class, key));
        assertEquals("CUSTOMER_DELAY", auditJson(key, "$.reasonType"));
        assertEquals(String.valueOf(first.getCandidateRevisionId()),
                auditJson(key, "$.candidateRevision.revisionId"));
        assertEquals("NONE", auditJson(key, "$.pendingChangeIdAfter"));
    }

    @Test
    void realPartialPatchUpdatesOnlySubmittedFactsAndRecalculatesDuration() {
        var plan = service.createInitial(command(keyPrefix + "-baseline-patch", "a".repeat(64)),
                actor(keyPrefix + "-baseline-patch"));
        var draft = changeService.createDraft(changeCommand(plan.getPlanId(), plan.getPlanVersion(),
                keyPrefix + "-patch-draft", "b".repeat(64)), actor(keyPrefix + "-patch-draft"));

        var reasonPatched = changeService.patchDraft(new PatchDurationChangeCommand(
                plan.getPlanId(), draft.getChangeId(), draft.getVersion(), 3,
                new DurationChangePatch(null, null, null, null, null, null, null, null,
                        Set.of("reasonDetail"))), actor(keyPrefix + "-patch-reason"));
        var durationPatched = changeService.patchDraft(new PatchDurationChangeCommand(
                plan.getPlanId(), draft.getChangeId(), reasonPatched.getVersion(), 3,
                new DurationChangePatch(null, null, null, 7, null, null, null, null,
                        Set.of("durationDays"))), actor(keyPrefix + "-patch-days"));

        assertEquals("", reasonPatched.getReasonDetail());
        assertEquals("CUSTOMER_DELAY", durationPatched.getReasonType());
        assertEquals(LocalDate.of(2026, 9, 16), durationPatched.getCandidateRevision().getEndDate());
        assertEquals(7, durationPatched.getCandidateRevision().getDurationDays());
        assertEquals(2, durationPatched.getVersion());
        assertEquals(1, durationPatched.getCandidateRevision().getVersion());
        assertEquals("客户延期", auditJson(keyPrefix + "-patch-reason", "$.before.reasonDetail"));
        assertEquals("", auditJson(keyPrefix + "-patch-reason", "$.after.reasonDetail"));
        assertEquals(String.valueOf(draft.getCandidateRevisionId()),
                auditJson(keyPrefix + "-patch-days", "$.candidateRevisionId"));
        assertEquals("5", auditJson(keyPrefix + "-patch-days",
                "$.before.candidateRevision.durationDays"));
        assertEquals("7", auditJson(keyPrefix + "-patch-days",
                "$.after.candidateRevision.durationDays"));
    }

    @Test
    void candidateInsertFailureRollsBackDraftAndIdempotency() {
        var plan = service.createInitial(command(keyPrefix + "-baseline-fault", "a".repeat(64)),
                actor(keyPrefix + "-baseline-fault"));
        String key = keyPrefix + "-draft-fault";
        revisionInsertFault.enabled = true;

        assertThrows(IllegalStateException.class, () -> changeService.createDraft(
                changeCommand(plan.getPlanId(), plan.getPlanVersion(), key, "b".repeat(64)), actor(key)));

        assertEquals(1L, count("sol_construction_plan_revision", "plan_id", plan.getPlanId()));
        assertEquals(0L, count("sol_construction_plan_change", "plan_id", plan.getPlanId()));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record "
                + "WHERE tenant_id=0 AND scope_code="
                + "'POST:/api/v1/pms/construction-plans/{id}/changes' AND actor_id=9 "
                + "AND idempotency_key=?", Long.class, key));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit "
                + "WHERE tenant_id=0 AND correlation_id=? AND result_code='REJECTED'", Long.class, key));
    }

    @Test
    void constructionPlanCommandsDoNotDoubleWriteLegacyScheduleOrChangeTables() {
        long legacyRowsBefore = legacyRowCount();
        var plan = service.createInitial(command(keyPrefix + "-no-legacy", "a".repeat(64)),
                actor(keyPrefix + "-no-legacy"));
        var draft = changeService.createDraft(changeCommand(plan.getPlanId(), plan.getPlanVersion(),
                keyPrefix + "-no-legacy-draft", "b".repeat(64)),
                actor(keyPrefix + "-no-legacy-draft"));
        changeService.patchDraft(new PatchDurationChangeCommand(
                plan.getPlanId(), draft.getChangeId(), draft.getVersion(), 3,
                new DurationChangePatch(null, null, null, 7, null, null, null, null,
                        Set.of("durationDays"))), actor(keyPrefix + "-no-legacy-patch"));

        assertEquals(legacyRowsBefore, legacyRowCount());
    }

    private long legacyRowCount() {
        return jdbcTemplate.queryForObject("SELECT "
                + "(SELECT COUNT(*) FROM pms_schedule_backward) + "
                + "(SELECT COUNT(*) FROM pms_schedule_backward_item) + "
                + "(SELECT COUNT(*) FROM pms_plan_change_request) + "
                + "(SELECT COUNT(*) FROM pms_plan_change_phase_snapshot)", Long.class);
    }

    private CreateInitialDurationCommand command(String key, String digest) {
        return new CreateInitialDurationCommand(projectId, "DATE_RANGE",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), 5, 3, key, digest);
    }

    private CreateDurationChangeCommand changeCommand(Long planId, Integer planVersion,
                                                       String key, String digest) {
        return new CreateDurationChangeCommand(planId, planVersion, 3,
                "DURATION_FROM_START", LocalDate.of(2026, 9, 10), null, 5,
                "CUSTOMER_DELAY", "客户延期", null, null, key, digest);
    }

    private ConstructionPlanApplicationService.Actor actor(String correlationId) {
        return new ConstructionPlanApplicationService.Actor(0L, 9L, correlationId);
    }

    private String auditJson(String correlationId, String path) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(JSON_UNQUOTE(JSON_EXTRACT(detail_snapshot, ?)), 'null') "
                        + "FROM plt_operation_audit WHERE tenant_id=0 AND correlation_id=? "
                        + "AND result_code='SUCCESS' ORDER BY id DESC LIMIT 1",
                String.class, path, correlationId);
    }

    private long count(String table, String column, Object value) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE tenant_id=0 AND " + column + "=?", Long.class, value);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            PlatformCommandExecutionApiImpl.class, OperationAuditApiImpl.class,
            ConstructionPlanApplicationService.class, DurationChangeApplicationService.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
        @Bean PermissionApi permissionApi() { return mock(PermissionApi.class); }
        @Bean ProjectScopeApi projectScopeApi() { return mock(ProjectScopeApi.class); }
        @Bean ProjectParticipantFactApi participantFactApi() { return mock(ProjectParticipantFactApi.class); }
        @Bean DictDataApi dictDataApi() { return mock(DictDataApi.class); }
        @Bean ConfigApi configApi() { return mock(ConfigApi.class); }
        @Bean BpmProcessInstanceApi bpmProcessInstanceApi() { return mock(BpmProcessInstanceApi.class); }
        @Bean DurationChangeProperties durationChangeProperties() { return new DurationChangeProperties(); }
        @Bean RevisionInsertFault revisionInsertFault() { return new RevisionInsertFault(); }

        @Bean
        @Primary
        ConstructionPlanRevisionMapper faultingRevisionMapper(
                @Qualifier("constructionPlanRevisionMapper") ConstructionPlanRevisionMapper delegate,
                RevisionInsertFault fault) {
            return (ConstructionPlanRevisionMapper) Proxy.newProxyInstance(
                    ConstructionPlanRevisionMapper.class.getClassLoader(),
                    new Class<?>[]{ConstructionPlanRevisionMapper.class}, (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (fault.enabled && "insert".equals(method.getName())) {
                                throw new IllegalStateException("CONSTRUCTION_PLAN_REVISION_WRITE_FAILED_TEST");
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        }
    }

    static final class RevisionInsertFault {
        volatile boolean enabled;
    }
}
