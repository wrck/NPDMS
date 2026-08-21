package cn.iocoder.yudao.module.system.service.transaction;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.audit.BusinessAuditApi;
import cn.iocoder.yudao.module.system.api.audit.dto.BusinessAuditCommand;
import cn.iocoder.yudao.module.system.api.businesscode.BusinessCodeApi;
import cn.iocoder.yudao.module.system.api.businesscode.dto.BusinessCodeAllocation;
import cn.iocoder.yudao.module.system.api.idempotency.TransactionalIdempotencyApi;
import cn.iocoder.yudao.module.system.api.idempotency.dto.IdempotencyDecision;
import cn.iocoder.yudao.module.system.api.outbox.TransactionalOutboxApi;
import cn.iocoder.yudao.module.system.api.outbox.dto.OutboxAppendCommand;
import cn.iocoder.yudao.module.system.dal.dataobject.audit.OperationAuditDO;
import cn.iocoder.yudao.module.system.dal.dataobject.businesscode.BusinessCodeRuleDO;
import cn.iocoder.yudao.module.system.dal.mysql.audit.OperationAuditMapper;
import cn.iocoder.yudao.module.system.dal.mysql.businesscode.BusinessCodeRuleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.idempotency.IdempotencyRecordMapper;
import cn.iocoder.yudao.module.system.dal.mysql.outbox.OutboxEventMapper;
import cn.iocoder.yudao.module.system.service.audit.BusinessAuditServiceImpl;
import cn.iocoder.yudao.module.system.service.businesscode.BusinessCodeServiceImpl;
import cn.iocoder.yudao.module.system.service.idempotency.TransactionalIdempotencyServiceImpl;
import cn.iocoder.yudao.module.system.service.outbox.TransactionalOutboxServiceImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Import({BusinessCodeServiceImpl.class, TransactionalIdempotencyServiceImpl.class,
        TransactionalOutboxServiceImpl.class, BusinessAuditServiceImpl.class})
class TransactionalBusinessSupportIntegrationTest extends BaseDbUnitTest {

    @Resource private BusinessCodeApi businessCodeApi;
    @Resource private TransactionalIdempotencyApi idempotencyApi;
    @Resource private TransactionalOutboxApi outboxApi;
    @Resource private BusinessAuditApi auditApi;
    @Resource private BusinessCodeRuleMapper businessCodeRuleMapper;
    @Resource private IdempotencyRecordMapper idempotencyRecordMapper;
    @Resource private OutboxEventMapper outboxEventMapper;
    @Resource private OperationAuditMapper operationAuditMapper;
    @Resource private PlatformTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void seedRule() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        businessCodeRuleMapper.insert(BusinessCodeRuleDO.builder()
                .id(1L).tenantId(1L).ruleCode("PROJECT_MANUAL").ruleVersion("V1")
                .prefix("MP").paddingWidth(8).nextValue(1L).status("ACTIVE")
                .effectiveFrom(LocalDateTime.now()).version(0).build());
    }

    @Test
    void allocateAndComplete_areTenantScopedAndReplayable() {
        BusinessCodeAllocation code = transactionTemplate.execute(status ->
                businessCodeApi.allocate(1L, "PROJECT_MANUAL"));
        assertNotNull(code);
        assertEquals("MP00000001", code.code());
        assertEquals("V1", code.ruleVersion());

        IdempotencyDecision first = transactionTemplate.execute(status -> {
            IdempotencyDecision decision = idempotencyApi.begin(
                    1L, 9L, "POST:/api/v1/pms/projects", "K1", "H1");
            idempotencyApi.complete(decision.recordId(), 100L, "{\"projectId\":100}");
            return decision;
        });
        assertNotNull(first);
        assertTrue(first.isOwner());

        IdempotencyDecision replay = transactionTemplate.execute(status -> idempotencyApi.begin(
                1L, 9L, "POST:/api/v1/pms/projects", "K1", "H1"));
        assertNotNull(replay);
        assertTrue(replay.isReplay());
        assertEquals(100L, replay.resourceId());
    }

    @Test
    void rollback_removesAllTransactionalParticipants_butFailureAuditCanCommitAfterward() {
        transactionTemplate.executeWithoutResult(status -> {
            businessCodeApi.allocate(1L, "PROJECT_MANUAL");
            idempotencyApi.begin(1L, 9L, "POST:/api/v1/pms/projects", "K2", "H2");
            auditApi.appendSuccess(audit("SUCCESS", 100L));
            outboxApi.append(new OutboxAppendCommand(
                    "EVT-1", 1L, "Project", 100L, "ProjectCreated", 1, "{}"));
            status.setRollbackOnly();
        });

        assertEquals(1L, businessCodeRuleMapper.selectById(1L).getNextValue());
        assertEquals(0L, idempotencyRecordMapper.selectCount(null));
        assertEquals(0L, outboxEventMapper.selectCount(null));
        assertEquals(0L, operationAuditMapper.selectCount(null));

        auditApi.appendFailureAfterRollback(audit("FAILED", null));
        OperationAuditDO failure = operationAuditMapper.selectOne(null);
        assertNotNull(failure);
        assertEquals("FAILED", failure.getDecisionCode());
        assertNull(failure.getResourceId());
    }

    @Test
    void begin_rejectsSameKeyWithDifferentRequestHash() {
        transactionTemplate.executeWithoutResult(status -> {
            IdempotencyDecision decision = idempotencyApi.begin(
                    1L, 9L, "POST:/api/v1/pms/projects", "K3", "H3");
            idempotencyApi.complete(decision.recordId(), 101L, "{\"projectId\":101}");
        });

        ServiceException exception = assertThrows(ServiceException.class,
                () -> transactionTemplate.executeWithoutResult(status -> idempotencyApi.begin(
                        1L, 9L, "POST:/api/v1/pms/projects", "K3", "DIFFERENT")));
        assertEquals(1_002_029_001, exception.getCode());
    }

    @Test
    void allocate_rejectsMissingOrMultipleActiveRules() {
        ServiceException missing = assertThrows(ServiceException.class,
                () -> transactionTemplate.executeWithoutResult(status ->
                        businessCodeApi.allocate(2L, "PROJECT_MANUAL")));
        assertEquals(1_002_029_000, missing.getCode());

        businessCodeRuleMapper.insert(BusinessCodeRuleDO.builder()
                .id(2L).tenantId(1L).ruleCode("PROJECT_MANUAL").ruleVersion("V2")
                .prefix("MP").paddingWidth(8).nextValue(1L).status("ACTIVE")
                .effectiveFrom(LocalDateTime.now()).version(0).build());
        ServiceException multiple = assertThrows(ServiceException.class,
                () -> transactionTemplate.executeWithoutResult(status ->
                        businessCodeApi.allocate(1L, "PROJECT_MANUAL")));
        assertEquals(1_002_029_000, multiple.getCode());
    }

    @Test
    void transactionalParticipants_rejectCallsWithoutOwningTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> businessCodeApi.allocate(1L, "PROJECT_MANUAL"));
        assertThrows(IllegalTransactionStateException.class,
                () -> idempotencyApi.begin(1L, 9L, "SCOPE", "K4", "H4"));
        assertThrows(IllegalTransactionStateException.class,
                () -> auditApi.appendSuccess(audit("SUCCESS", 100L)));
        assertThrows(IllegalTransactionStateException.class,
                () -> outboxApi.append(new OutboxAppendCommand(
                        "EVT-2", 1L, "Project", 100L, "ProjectCreated", 1, "{}")));
    }

    private static BusinessAuditCommand audit(String decision, Long resourceId) {
        return new BusinessAuditCommand(1L, 9L, "PROJECT_CREATE", "Project", resourceId,
                decision, "CORR-1", "{\"schemaVersion\":1}");
    }
}
