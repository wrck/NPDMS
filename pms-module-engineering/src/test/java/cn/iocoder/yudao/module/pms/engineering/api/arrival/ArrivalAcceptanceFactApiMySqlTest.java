package cn.iocoder.yudao.module.pms.engineering.api.arrival;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFact;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalAcceptanceFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalDifferenceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ArrivalAcceptanceFactApiMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArrivalAcceptanceFactApiMySqlTest {

    @Resource
    private ArrivalAcceptanceFactApi api;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TestDeliveryScopePort deliveryScopePort;

    private long projectId;
    private long idBase;

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
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        projectId = 979_300_000_000L + suffix;
        idBase = 979_400_000_000L + suffix * 10;
        insertConfirmed(idBase + 1, 1L, idBase + 101, 11L);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE d FROM imp_arrival_difference d "
                + "JOIN imp_arrival_acceptance a ON a.tenant_id=d.tenant_id "
                + "AND a.id=d.arrival_acceptance_id WHERE a.tenant_id=0 AND a.project_id=?", projectId);
        jdbcTemplate.update("DELETE l FROM imp_arrival_line l "
                + "JOIN imp_arrival_acceptance a ON a.tenant_id=l.tenant_id "
                + "AND a.id=l.arrival_acceptance_id WHERE a.tenant_id=0 AND a.project_id=?", projectId);
        jdbcTemplate.update("DELETE FROM imp_arrival_acceptance WHERE tenant_id=0 AND project_id=?", projectId);
        TenantContextHolder.clear();
    }

    @Test
    void proxiedReadOnlyApiMakesOldFactVersionStaleAfterNewConfirmation() {
        assertTrue(AopUtils.isAopProxy(api));
        ArrivalAcceptanceFact first = api.inspect(query());
        assertEquals(1L, first.factVersion());
        assertEquals(List.of(idBase + 1), first.sourceAcceptanceIds());

        insertConfirmed(idBase + 2, 2L, idBase + 102, 12L);

        ArrivalAcceptanceFact stale = api.lockAndRevalidate(new ArrivalAcceptanceFactRevalidationQuery(
                0L, projectId, Set.of(11L, 12L), List.of(), first.factVersion(),
                new ArrivalScopeWatermark(5L, Map.of(11L, 7L, 12L, 8L))));
        assertEquals(ArrivalAcceptanceFact.DECISION_STALE, stale.decision());
        assertEquals(2L, stale.factVersion());
        assertEquals(List.of(idBase + 1, idBase + 2), stale.sourceAcceptanceIds());
    }

    @Test
    void concurrentReopenMakesPreviouslyInspectedVersionStale() throws Exception {
        ArrivalAcceptanceFact first = api.inspect(query());
        deliveryScopePort.blockNextLock();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ArrivalAcceptanceFact> revalidation = executor.submit(() -> {
                TenantContextHolder.setTenantId(0L);
                try {
                    return api.lockAndRevalidate(new ArrivalAcceptanceFactRevalidationQuery(
                            0L, projectId, Set.of(11L, 12L), List.of(), first.factVersion(),
                            first.scopeWatermark()));
                } finally {
                    TenantContextHolder.clear();
                }
            });
            assertTrue(deliveryScopePort.awaitLockEntry());
            insertFactAffectingDifference(idBase + 201, idBase + 1, idBase + 101, 2L);
            deliveryScopePort.releaseLock();

            ArrivalAcceptanceFact stale = revalidation.get(10, TimeUnit.SECONDS);
            assertEquals(ArrivalAcceptanceFact.DECISION_STALE, stale.decision());
            assertEquals(2L, stale.factVersion());
            assertTrue(stale.reopened());
            assertEquals(List.of(idBase + 1), stale.sourceAcceptanceIds());
        } finally {
            deliveryScopePort.releaseLock();
            executor.shutdownNow();
        }
    }

    @Test
    void inspectDoesNotWriteAuditOrSecondCompletionTable() {
        long auditBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0", Long.class);

        ArrivalAcceptanceFact fact = api.inspect(
                new ArrivalAcceptanceFactQuery(0L, projectId, Set.of(11L), List.of()));

        assertEquals(ArrivalAcceptanceFact.DECISION_ACCEPTED, fact.decision());
        assertEquals(auditBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=0", Long.class));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema=DATABASE() "
                        + "AND table_name='imp_implementation_readiness_snapshot'", Long.class));
    }

    private ArrivalAcceptanceFactQuery query() {
        return new ArrivalAcceptanceFactQuery(0L, projectId, Set.of(11L, 12L), List.of());
    }

    private void insertConfirmed(long acceptanceId, long factVersion, long lineId, long deviceId) {
        jdbcTemplate.update("INSERT INTO imp_arrival_acceptance "
                        + "(id,project_id,batch_code,batch_root_marker,logistics_no,arrived_at,signer_snapshot,status,"
                        + "project_version,project_participant_fact_version,project_scope_version,"
                        + "delivery_scope_version,expected_scope_snapshot,scope_watermark,"
                        + "migration_resolution_status,project_fact_version,version,tenant_id) "
                        + "VALUES (?,?,?,1,?,NOW(),JSON_OBJECT('name','test'),'CONFIRMED',1,1,1,5,"
                        + "JSON_OBJECT(),JSON_OBJECT(),'NOT_APPLICABLE',?,0,0)",
                acceptanceId, projectId, "B-" + acceptanceId, "L-" + acceptanceId, factVersion);
        long assignmentVersion = deviceId == 11L ? 7L : 8L;
        jdbcTemplate.update("INSERT INTO imp_arrival_line "
                        + "(id,arrival_acceptance_id,line_no,line_revision,scope_type,device_id,"
                        + "device_assignment_version,expected_quantity,accepted_quantity,unit,status,"
                        + "current_marker,version,tenant_id) VALUES (?,?,1,1,'DEVICE',?,?,1,1,'SET',"
                + "'ACCEPTED',1,0,0)", lineId, acceptanceId, deviceId, assignmentVersion);
    }

    private void insertFactAffectingDifference(long differenceId, long acceptanceId,
                                                long lineId, long factVersion) {
        jdbcTemplate.update("INSERT INTO imp_arrival_difference "
                        + "(id,arrival_acceptance_id,arrival_line_id,difference_no,revision_no,"
                        + "difference_type,resolution_status,reason,scope_snapshot,project_fact_version,"
                        + "fact_impact_type,current_marker,version,tenant_id) "
                        + "VALUES (?,?,?,1,1,'EVIDENCE_INCOMPLETE','OPEN','reopened source',"
                        + "JSON_OBJECT('scopeType','DEVICE','deviceId',11),?,'REOPEN',1,0,0)",
                differenceId, acceptanceId, lineId, factVersion);
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少环境变量：" + name);
        return value;
    }

    @SpringBootConfiguration
    @MapperScan("cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class})
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TestDeliveryScopePort deliveryScopePort() {
            return new TestDeliveryScopePort();
        }

        @Bean
        DeviceScopeFactPort deviceScopeFactPort() {
            return new TestDeviceScopeFactPort();
        }

        @Bean
        ArrivalAcceptanceFactApi arrivalAcceptanceFactApi(
                ArrivalAcceptanceMapper acceptanceMapper,
                ArrivalLineMapper lineMapper,
                ArrivalDifferenceMapper differenceMapper,
                DeliveryScopePort deliveryScopePort,
                DeviceScopeFactPort deviceScopeFactPort) {
            return new ArrivalAcceptanceFactApiImpl(acceptanceMapper, lineMapper, differenceMapper,
                    deliveryScopePort, deviceScopeFactPort);
        }

    }

    static class TestDeliveryScopePort implements DeliveryScopePort {

        private volatile CountDownLatch lockEntered;
        private volatile CountDownLatch lockReleased;

        @Override
        public AssignedScope inspectAssignedScope(Long projectId) {
            return scope(projectId);
        }

        @Override
        public AssignedScope lockAndRevalidate(Long projectId, Long expectedScopeVersion) {
            if (!Long.valueOf(5L).equals(expectedScopeVersion)) {
                throw new IllegalStateException("stale test delivery scope");
            }
            CountDownLatch entered = lockEntered;
            CountDownLatch released = lockReleased;
            if (entered != null && released != null) {
                entered.countDown();
                try {
                    if (!released.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test delivery scope lock timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("test delivery scope lock interrupted", exception);
                }
            }
            return scope(projectId);
        }

        void blockNextLock() {
            lockEntered = new CountDownLatch(1);
            lockReleased = new CountDownLatch(1);
        }

        boolean awaitLockEntry() throws InterruptedException {
            return lockEntered.await(10, TimeUnit.SECONDS);
        }

        void releaseLock() {
            CountDownLatch released = lockReleased;
            if (released != null) {
                released.countDown();
            }
            lockEntered = null;
            lockReleased = null;
        }

        private AssignedScope scope(Long projectId) {
            return new AssignedScope(projectId, 5L, List.of(
                    new AssignedLine(1L, BigDecimal.valueOf(2), "SET", "P", "M",
                            Set.of("SN-11", "SN-12"))));
        }
    }

    static class TestDeviceScopeFactPort implements DeviceScopeFactPort {
        @Override
        public DeviceScopeFact resolveBySerials(Long tenantId, Long projectId, Set<String> serialNumbers) {
            return scope(projectId, serialNumbers);
        }

        @Override
        public DeviceScopeFact lockAndRevalidate(Long tenantId, Long projectId,
                                                 List<ExpectedDeviceFact> expectedDevices) {
            return scope(projectId, expectedDevices.stream().map(ExpectedDeviceFact::serialNumber)
                    .collect(java.util.stream.Collectors.toSet()));
        }

        private DeviceScopeFact scope(Long projectId, Set<String> serialNumbers) {
            List<DeviceFact> devices = serialNumbers.stream().sorted().map(serial -> {
                long id = "SN-11".equals(serial) ? 11L : 12L;
                long version = id == 11L ? 7L : 8L;
                return new DeviceFact(id, serial, projectId, version);
            }).toList();
            return new DeviceScopeFact(projectId, devices);
        }
    }
}
