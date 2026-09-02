package cn.iocoder.yudao.module.pms.service.inspectionrule;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformIdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOperationAuditMapper;
import cn.iocoder.yudao.module.pms.platform.service.command.OperationAuditApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRulePublicationService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.InspectionRulePublicationServiceImpl;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.audit.InspectionRulePublicationAuditService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleActionPermissionGuard;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_IDEMPOTENCY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = InspectionRulePublicationMySqlIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InspectionRulePublicationMySqlIntegrationTest {

    private static final long TENANT_ID = 0L;
    private static final long ACTOR_ID = 9L;

    @Resource
    private InspectionRulePublicationService publicationService;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PlatformWriteFault platformWriteFault;

    private long ruleId;
    private long revisionId;
    private String testPrefix;
    private String idempotencyKey;
    private String correlationId;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> environment = System.getenv();
        String database = environment.getOrDefault("NPDMS_DB_NAME", "");
        if (!"npdms_test".equals(database)) {
            throw new IllegalStateException(
                    "InspectionRulePublicationMySqlIntegrationTest requires NPDMS_DB_NAME=npdms_test");
        }
        String port = environment.getOrDefault("NPDMS_MYSQL_PORT", "23316");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(environment, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(environment, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.service");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "ASSIGN_ID");
    }

    @BeforeEach
    void setUp() {
        setRequestContext();
        platformWriteFault.clear();
        testPrefix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        idempotencyKey = "fins001-disable-" + testPrefix;
        correlationId = "fins001-disable-corr-" + testPrefix;
        insertPublishedRevision();
    }

    @AfterEach
    void tearDown() {
        try {
            platformWriteFault.clear();
            jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE correlation_id LIKE ?",
                    "fins001-disable-corr-" + testPrefix + "%");
            jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE idempotency_key LIKE ?",
                    "fins001-disable-" + testPrefix + "%");
            jdbcTemplate.update("DELETE FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=?",
                    TENANT_ID, ruleId);
            jdbcTemplate.update("DELETE FROM srv_inspection_rule WHERE tenant_id=? AND id=?", TENANT_ID, ruleId);
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void disableCommitsRevisionIdempotencyAndSuccessAuditAndReplaysWithoutDuplicateWrites() {
        InspectionRulePublicationService.DisableCommand command = command(1, idempotencyKey, correlationId);

        InspectionRulePublicationService.DisableResult first = publicationService.disable(command);
        InspectionRulePublicationService.DisableResult replay = publicationService.disable(command);

        assertEquals("DISABLED", first.statusCode());
        assertEquals(2, first.version());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertRevision("DISABLED", 2);
        assertEquals(1L, idempotencyCount(idempotencyKey));
        assertEquals("COMPLETED", idempotencyStatus(idempotencyKey));
        assertEquals(1L, auditCount(correlationId, "SUCCESS"));
        assertEquals(0L, auditCount(correlationId, "REJECTED"));
    }

    @Test
    void sameKeyDifferentPayloadKeepsCompletedBusinessStateAndWritesOnlyRejectionAudit() {
        publicationService.disable(command(1, idempotencyKey, correlationId));
        String conflictCorrelationId = correlationId + "-conflict";

        ServiceException failure = assertThrows(ServiceException.class,
                () -> publicationService.disable(command(2, idempotencyKey, conflictCorrelationId)));

        assertEquals(INSPECTION_RULE_IDEMPOTENCY_CONFLICT.getCode(), failure.getCode());
        assertRevision("DISABLED", 2);
        assertEquals(1L, idempotencyCount(idempotencyKey));
        assertEquals(1L, auditCount(correlationId, "SUCCESS"));
        assertEquals(1L, auditCount(conflictCorrelationId, "REJECTED"));
        String detail = auditDetail(conflictCorrelationId, "REJECTED");
        assertFalse(detail.contains(idempotencyKey));
        assertFalse(detail.contains("commandContent"));
        assertFalse(detail.contains("expectedResultRegex"));
    }

    @Test
    void successAuditFailureRollsBackRevisionIdempotencyAndSuccessAuditButKeepsRejectionAudit() {
        platformWriteFault.successAuditInsert = true;

        assertThrows(IllegalStateException.class,
                () -> publicationService.disable(command(1, idempotencyKey, correlationId)));

        platformWriteFault.clear();
        assertRevision("PUBLISHED", 1);
        assertEquals(0L, idempotencyCount(idempotencyKey));
        assertEquals(0L, auditCount(correlationId, "SUCCESS"));
        assertEquals(1L, auditCount(correlationId, "REJECTED"));
    }

    @Test
    void idempotencyCompletionFailureRollsBackRevisionAndReservationWithoutSuccessAudit() {
        platformWriteFault.idempotencyCompletionUpdate = true;

        assertThrows(IllegalStateException.class,
                () -> publicationService.disable(command(1, idempotencyKey, correlationId)));

        platformWriteFault.clear();
        assertRevision("PUBLISHED", 1);
        assertEquals(0L, idempotencyCount(idempotencyKey));
        assertEquals(0L, auditCount(correlationId, "SUCCESS"));
        assertEquals(1L, auditCount(correlationId, "REJECTED"));
    }

    private InspectionRulePublicationService.DisableCommand command(
            int expectedVersion,
            String key,
            String correlation) {
        return new InspectionRulePublicationService.DisableCommand(
                revisionId, expectedVersion, key, correlation);
    }

    private void insertPublishedRevision() {
        jdbcTemplate.update("INSERT INTO srv_inspection_rule "
                        + "(detection_id,rule_name,version,tenant_id) VALUES (?,?,0,?)",
                "IT-PUB-" + testPrefix, "发布事务规则-" + testPrefix, TENANT_ID);
        ruleId = jdbcTemplate.queryForObject(
                "SELECT id FROM srv_inspection_rule WHERE tenant_id=? AND detection_id=?",
                Long.class, TENANT_ID, "IT-PUB-" + testPrefix);
        jdbcTemplate.update("INSERT INTO srv_inspection_rule_revision "
                        + "(rule_id,revision_no,status_code,rule_name_snapshot,inspection_item,description,"
                        + "category_code,category_name_snapshot,severity_code,severity_name_snapshot,sort_order,"
                        + "expected_result_regex,threshold_data_type,threshold_operator,threshold_value,threshold_unit,"
                        + "published_by,published_at,version,tenant_id) "
                        + "VALUES (?,1,'PUBLISHED',?,'CPU利用率','检查CPU利用率','BASIC','基础检测',"
                        + "'GENERAL','一般',10,'^CPU: [0-9]+$','NUMBER','≤',80,'%',?,CURRENT_TIMESTAMP(3),1,?)",
                ruleId, "发布事务规则-" + testPrefix, ACTOR_ID, TENANT_ID);
        revisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM srv_inspection_rule_revision WHERE tenant_id=? AND rule_id=? AND revision_no=1",
                Long.class, TENANT_ID, ruleId);
    }

    private void assertRevision(String status, int version) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status_code,version,disabled_by,disabled_at FROM srv_inspection_rule_revision "
                        + "WHERE tenant_id=? AND id=?",
                TENANT_ID, revisionId);
        assertEquals(status, row.get("status_code"));
        assertEquals(version, ((Number) row.get("version")).intValue());
        if ("DISABLED".equals(status)) {
            assertEquals(ACTOR_ID, ((Number) row.get("disabled_by")).longValue());
            assertTrue(row.get("disabled_at") != null);
        } else {
            assertEquals(null, row.get("disabled_by"));
            assertEquals(null, row.get("disabled_at"));
        }
    }

    private long idempotencyCount(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? AND scope_code=? "
                        + "AND actor_id=? AND idempotency_key=?",
                Long.class, TENANT_ID, "INSPECTION_RULE_DISABLE", ACTOR_ID, key);
    }

    private String idempotencyStatus(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM plt_idempotency_record WHERE tenant_id=? AND scope_code=? "
                        + "AND actor_id=? AND idempotency_key=?",
                String.class, TENANT_ID, "INSPECTION_RULE_DISABLE", ACTOR_ID, key);
    }

    private long auditCount(String correlation, String resultCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? AND operation_code=? "
                        + "AND aggregate_type=? AND aggregate_key=? AND actor_id=? AND correlation_id=? "
                        + "AND result_code=?",
                Long.class, TENANT_ID, "INSPECTION_RULE_DISABLE", "InspectionRuleRevision",
                String.valueOf(revisionId), ACTOR_ID, correlation, resultCode);
    }

    private String auditDetail(String correlation, String resultCode) {
        return jdbcTemplate.queryForObject(
                "SELECT detail_snapshot FROM plt_operation_audit WHERE tenant_id=? AND operation_code=? "
                        + "AND aggregate_key=? AND correlation_id=? AND result_code=?",
                String.class, TENANT_ID, "INSPECTION_RULE_DISABLE", String.valueOf(revisionId),
                correlation, resultCode);
    }

    private void setRequestContext() {
        TenantContextHolder.setTenantId(TENANT_ID);
        SecurityFrameworkUtils.setLoginUser(
                new LoginUser().setId(ACTOR_ID).setUserType(2),
                new MockHttpServletRequest());
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量：" + name);
        }
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({
            "cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"
    })
    @Import({
            YudaoDataSourceAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class,
            SpringUtil.class,
            PlatformCommandExecutionApiImpl.class,
            PlatformTransactionalOutboxWriter.class,
            OperationAuditApiImpl.class,
            InspectionRulePublicationServiceImpl.class,
            InspectionRulePublicationAuditService.class,
            InspectionRuleActionPermissionGuard.class
    })
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PermissionApi permissionApi() {
            PermissionApi permissionApi = mock(PermissionApi.class);
            when(permissionApi.hasAnyPermissions(any(), any())).thenReturn(true);
            return permissionApi;
        }

        @Bean
        PlatformWriteFault platformWriteFault() {
            return new PlatformWriteFault();
        }

        @Bean
        @Primary
        PlatformOperationAuditMapper faultingAuditMapper(
                @Qualifier("platformOperationAuditMapper") PlatformOperationAuditMapper delegate,
                PlatformWriteFault fault) {
            return proxy(PlatformOperationAuditMapper.class, delegate, (method, arguments) ->
                            fault.successAuditInsert
                                    && "insert".equals(method)
                                    && arguments != null
                                    && arguments.length == 1
                                    && arguments[0] instanceof PlatformOperationAuditDO audit
                                    && "SUCCESS".equals(audit.getResultCode()),
                    "INSPECTION_RULE_SUCCESS_AUDIT_WRITE_FAILED_TEST");
        }

        @Bean
        @Primary
        PlatformIdempotencyRecordMapper faultingIdempotencyMapper(
                @Qualifier("platformIdempotencyRecordMapper") PlatformIdempotencyRecordMapper delegate,
                PlatformWriteFault fault) {
            return proxy(PlatformIdempotencyRecordMapper.class, delegate, (method, arguments) ->
                            fault.idempotencyCompletionUpdate
                                    && "updateById".equals(method)
                                    && arguments != null
                                    && arguments.length == 1
                                    && arguments[0] instanceof PlatformIdempotencyRecordDO record
                                    && "COMPLETED".equals(record.getStatus()),
                    "INSPECTION_RULE_IDEMPOTENCY_COMPLETION_WRITE_FAILED_TEST");
        }

        private static <T> T proxy(
                Class<T> type,
                T delegate,
                FaultPredicate predicate,
                String failureMessage) {
            return type.cast(Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (predicate.shouldFail(method.getName(), arguments)) {
                                throw new IllegalStateException(failureMessage);
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }));
        }
    }

    @FunctionalInterface
    interface FaultPredicate {
        boolean shouldFail(String method, Object[] arguments);
    }

    static final class PlatformWriteFault {
        volatile boolean successAuditInsert;
        volatile boolean idempotencyCompletionUpdate;

        void clear() {
            successAuditInsert = false;
            idempotencyCompletionUpdate = false;
        }
    }
}
