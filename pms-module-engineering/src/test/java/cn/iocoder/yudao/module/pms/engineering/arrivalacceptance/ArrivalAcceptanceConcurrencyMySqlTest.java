package cn.iocoder.yudao.module.pms.engineering.arrivalacceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.ArrivalAcceptanceApplicationService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = ArrivalAcceptanceApplicationMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ArrivalAcceptanceConcurrencyMySqlTest {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        ArrivalAcceptanceApplicationMySqlTest.mysqlProperties(registry);
    }

    @Resource JdbcTemplate jdbcTemplate;
    @Resource ArrivalAcceptanceApplicationService applicationService;
    @Resource cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalLineMapper lineMapper;
    @Resource cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceMapper evidenceMapper;
    @Resource cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.DeliveryEvidenceRevisionMapper revisionMapper;
    @Resource ArrivalAcceptanceApplicationMySqlTest.TestOwnerPorts ownerPorts;

    long tenantId;
    long projectId;
    ArrivalAcceptanceApplicationMySqlTest support;

    @BeforeEach
    void setUp() {
        long suffix = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
        tenantId = 979_900_000_000L + suffix;
        projectId = 980_000_000_000L + suffix;
        TenantContextHolder.setTenantId(tenantId);
        ownerPorts.reset(projectId);
        support = new ArrivalAcceptanceApplicationMySqlTest();
        support.jdbcTemplate = jdbcTemplate;
        support.applicationService = applicationService;
        support.lineMapper = lineMapper;
        support.evidenceMapper = evidenceMapper;
        support.revisionMapper = revisionMapper;
        support.ownerPorts = ownerPorts;
        support.tenantId = tenantId;
        support.projectId = projectId;
    }

    @AfterEach
    void tearDown() {
        ArrivalAcceptanceApplicationMySqlTest.deleteTenantData(jdbcTemplate, tenantId);
        TenantContextHolder.clear();
    }

    @Test
    void concurrentConfirmationProducesOneFactVersionOneEventAndOneSuccessAudit() throws Exception {
        ArrivalAcceptanceDO draft = applicationService.createDraft(
                new ArrivalAcceptanceApplicationService.CreateDraftCommand(
                        tenantId, projectId, ArrivalAcceptanceApplicationMySqlTest.ACTOR_ID,
                        "B-concurrent", "L-concurrent", LocalDateTime.of(2026, 8, 30, 10, 0),
                        "签收人", 8L, "create-concurrent", "corr-create-concurrent"));
        support.attachAcceptedDeviceAndEvidence(draft);
        applicationService.submit(new ArrivalAcceptanceApplicationService.SubmitCommand(
                tenantId, draft.getId(), ArrivalAcceptanceApplicationMySqlTest.ACTOR_ID,
                0, "submit-concurrent", "corr-submit-concurrent"));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> confirm(draft.getId(), "confirm-a", ready, start));
            Future<Boolean> second = executor.submit(() -> confirm(draft.getId(), "confirm-b", ready, start));
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("workers not ready");
            start.countDown();

            int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
                "SELECT status FROM imp_arrival_acceptance WHERE tenant_id=? AND id=?",
                String.class, tenantId, draft.getId()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT project_fact_version FROM imp_arrival_acceptance WHERE tenant_id=? AND id=?",
                Long.class, tenantId, draft.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_outbox_event WHERE tenant_id=? "
                        + "AND event_type='ImplementationEvidencePublished'", Integer.class, tenantId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_operation_audit WHERE tenant_id=? "
                        + "AND operation_code='ARRIVAL_ACCEPTANCE_CONFIRM'", Integer.class, tenantId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plt_idempotency_record WHERE tenant_id=? "
                        + "AND scope_code=? AND status='COMPLETED'", Integer.class,
                tenantId, "IMP:ARRIVAL_CONFIRM:" + draft.getId()));
    }

    private boolean confirm(long acceptanceId, String suffix,
                            CountDownLatch ready, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(tenantId);
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
            applicationService.confirm(new ArrivalAcceptanceApplicationService.ConfirmCommand(
                    tenantId, acceptanceId, ArrivalAcceptanceApplicationMySqlTest.ACTOR_ID,
                    1, suffix, "corr-" + suffix));
            return true;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            TenantContextHolder.clear();
        }
    }
}
