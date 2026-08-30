package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommands.*;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformCommandExecutionApiImpl;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.pms.commerce.service.scope.CommerceDeliveryScopeCommandException.Code.OVER_ALLOCATION;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = CommerceDeliveryScopeCommandMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommerceDeliveryScopeCommandMySqlTest {

    private static final long TENANT = 990_005L;
    private static final long ACTOR = 51L;

    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private CommerceDeliveryScopeCommandService service;
    @Resource private ProjectParticipantFactApi participantFactApi;
    @Resource private ProjectScopeApi projectScopeApi;
    @Resource private DeviceScopeFactApi deviceScopeFactApi;
    @Resource private AssetLocationApi assetLocationApi;
    @Resource private AtomicReference<String> stage;

    private long orderLineId;
    private String suffix;

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
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.commerce");
        registry.add("mybatis-plus.configuration.map-underscore-to-camel-case", () -> "true");
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        suffix = Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits(), 36);
        orderLineId = 990_500_000_000L + Math.abs(suffix.hashCode());
        stage.set("S4");
        reset(participantFactApi, projectScopeApi, deviceScopeFactApi, assetLocationApi);
        when(participantFactApi.inspect(any())).thenAnswer(invocation -> participant(
                invocation.<cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery>getArgument(0).projectId()));
        when(participantFactApi.lockAndRevalidate(any())).thenAnswer(invocation -> participant(
                invocation.<cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery>getArgument(0).projectId()));
        when(projectScopeApi.resolveCurrent(any())).thenAnswer(invocation -> {
            var query = invocation.<cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery>getArgument(0);
            return new ProjectScopeResult(query.anchorProjectId(), 3L, Set.of(query.anchorProjectId()), Set.of());
        });
        when(projectScopeApi.lockAndRevalidate(any())).thenAnswer(invocation -> {
            var query = invocation.<cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery>getArgument(0);
            return new ProjectScopeResult(query.anchorProjectId(), 3L, Set.of(query.anchorProjectId()), Set.of());
        });
        insertOrderLine();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM plt_operation_audit WHERE tenant_id=? AND aggregate_type='DeliveryScopeProject'", TENANT);
        jdbcTemplate.update("DELETE FROM plt_idempotency_record WHERE tenant_id=? AND scope_code LIKE 'COM:DELIVERY_SCOPE:%'", TENANT);
        jdbcTemplate.update("DELETE FROM com_outbox_event WHERE tenant_id=?", TENANT);
        jdbcTemplate.update("DELETE FROM com_delivery_scope_detail WHERE tenant_id=?", TENANT);
        jdbcTemplate.update("DELETE FROM com_delivery_scope WHERE tenant_id=?", TENANT);
        jdbcTemplate.update("DELETE FROM com_delivery_scope_project_version WHERE tenant_id=?", TENANT);
        jdbcTemplate.update("DELETE FROM com_order_line WHERE tenant_id=?", TENANT);
        TenantContextHolder.clear();
    }

    @Test
    void applyAndReplayPersistOneScopeWatermarkAuditAndOutbox() {
        ApplyCommand command = apply(101L, "6", "IDEM-A-" + suffix);

        CommandResult first = service.apply(command);
        CommandResult replay = service.apply(command);

        assertEquals(first, replay);
        assertEquals(1, count("com_delivery_scope"));
        assertEquals(1, count("com_delivery_scope_detail"));
        assertEquals(1, count("com_outbox_event"));
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT scope_version FROM com_delivery_scope_project_version "
                + "WHERE tenant_id=? AND project_id=?", Long.class, TENANT, 101L));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                + "AND aggregate_type='DeliveryScopeProject'", Integer.class, TENANT));
        verifyNoInteractions(deviceScopeFactApi, assetLocationApi);
    }

    @Test
    void concurrentProjectsCannotExceedAuthoritativeQuantity() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> applyConcurrently(201L, "6", "IDEM-C1-" + suffix, ready, start));
            Future<Boolean> second = executor.submit(() -> applyConcurrently(202L, "6", "IDEM-C2-" + suffix, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals(1, count("com_delivery_scope"));
            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                    + "AND scope_code='COM:DELIVERY_SCOPE:APPLY' AND status='COMPLETED'", Integer.class, TENANT));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void protectedReleaseAppendsConflictWithoutReleasedEvent() {
        service.apply(apply(301L, "4", "IDEM-P-A-" + suffix));
        stage.set("S5");

        CommandResult result = service.release(new ReleaseCommand(TENANT, 301L, ACTOR, 1L,
                List.of(orderLineId), "PROJECT_PROTECTED", "manager-release",
                "IDEM-P-R-" + suffix, "CORR-P-R-" + suffix));

        assertTrue(result.protectedAsConflict());
        assertEquals(List.of("ACTIVE", "CONFLICT"), jdbcTemplate.queryForList(
                "SELECT scope_status FROM com_delivery_scope WHERE tenant_id=? AND project_id=? ORDER BY allocation_version",
                String.class, TENANT, 301L));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM com_outbox_event WHERE tenant_id=? "
                + "AND event_type='DeliveryScopeAssigned'", Integer.class, TENANT));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM com_outbox_event WHERE tenant_id=? "
                + "AND event_type='DeliveryScopeReleased'", Integer.class, TENANT));
        assertEquals(2L, jdbcTemplate.queryForObject("SELECT scope_version FROM com_delivery_scope_project_version "
                + "WHERE tenant_id=? AND project_id=?", Long.class, TENANT, 301L));
    }

    @Test
    void protectedAdjustmentKeepsPriorQuantityAsConflictWithoutOutbox() {
        service.apply(apply(401L, "6", "IDEM-AJ-A-" + suffix));
        stage.set("S5");

        ApplyCommand decrease = new ApplyCommand(TENANT, 401L, ACTOR, 1L,
                apply(401L, "3", "unused").lines(), "PROTECTED_ADJUST",
                "IDEM-AJ-R-" + suffix, "CORR-AJ-R-" + suffix);
        CommandResult result = service.apply(decrease);

        assertTrue(result.protectedAsConflict());
        assertEquals(List.of("ACTIVE", "CONFLICT"), jdbcTemplate.queryForList(
                "SELECT scope_status FROM com_delivery_scope WHERE tenant_id=? AND project_id=? ORDER BY allocation_version",
                String.class, TENANT, 401L));
        assertEquals(new BigDecimal("6.000000"), jdbcTemplate.queryForObject(
                "SELECT allocated_qty FROM com_delivery_scope WHERE tenant_id=? AND project_id=? "
                        + "AND scope_status='CONFLICT'", BigDecimal.class, TENANT, 401L));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM com_outbox_event WHERE tenant_id=?",
                Integer.class, TENANT));
    }

    private boolean applyConcurrently(Long projectId, String quantity, String key,
                                      CountDownLatch ready, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(TENANT);
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            try {
                service.apply(apply(projectId, quantity, key));
                return true;
            } catch (CommerceDeliveryScopeCommandException exception) {
                if (exception.getCode() == OVER_ALLOCATION) return false;
                throw exception;
            }
        } finally {
            TenantContextHolder.clear();
        }
    }

    private ApplyCommand apply(Long projectId, String quantity, String key) {
        BigDecimal value = new BigDecimal(quantity);
        Location location = new Location(LocationResolution.UNRESOLVED, null, null, null, null, "待权威解析");
        ScopeDetail detail = new ScopeDetail("OFFICE-A", value, "PCS", "ITEM-1", null, null, location);
        ScopeLine line = new ScopeLine(orderLineId, "V1", value, "PCS", List.of(detail));
        return new ApplyCommand(TENANT, projectId, ACTOR, 0L, List.of(line),
                "INITIAL_ASSIGNMENT", key, "CORR-" + key);
    }

    private ProjectParticipantFact participant(Long projectId) {
        return new ProjectParticipantFact(projectId, ACTOR, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                "MANAGER", "ACTIVE", stage.get(), 1, 2L);
    }

    private void insertOrderLine() {
        jdbcTemplate.update("INSERT INTO com_order_line "
                        + "(id,source_system,source_key,source_version,order_id,line_code,item_code,model_code,quantity,unit_code,"
                        + "quantity_status,source_lifecycle_status,source_updated_at,synced_at,version,creator,create_time,updater,"
                        + "update_time,deleted,tenant_id) VALUES (?,?,?,?,?,'10','ITEM-1',NULL,10,'PCS','CONFIRMED','ACTIVE',"
                        + "NOW(3),NOW(3),0,'0',NOW(3),'0',NOW(3),b'0',?)",
                orderLineId, "ERP", "LINE-" + suffix, "V1", orderLineId + 1, TENANT);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=?", Integer.class, TENANT);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan({"cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope",
            "cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox",
            "cn.iocoder.yudao.module.pms.platform.dal.mysql.command"})
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            CommerceDeliveryScopeCommandService.class, ProjectScopeQualificationAdapter.class,
            DeviceAndLocationFactAdapter.class, PlatformCommandExecutionApiImpl.class,
            PlatformTransactionalOutboxWriter.class})
    static class TestApplication {
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean ProjectParticipantFactApi participantFactApi() { return mock(ProjectParticipantFactApi.class); }
        @Bean ProjectScopeApi projectScopeApi() { return mock(ProjectScopeApi.class); }
        @Bean DeviceScopeFactApi deviceScopeFactApi() { return mock(DeviceScopeFactApi.class); }
        @Bean AssetLocationApi assetLocationApi() { return mock(AssetLocationApi.class); }
        @Bean AtomicReference<String> stage() { return new AtomicReference<>("S4"); }
    }
}
