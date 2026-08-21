package cn.iocoder.yudao.module.pms.project.service.acceptance;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.pms.project.api.acceptance.AcceptanceDeliverableInitializationApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableInitializationResult;
import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableRequirementSnapshot;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(AcceptanceDeliverableInitializationApiImpl.class)
class AcceptanceDeliverableInitializationIntegrationTest extends BaseDbUnitTest {

    @Resource private AcceptanceDeliverableInitializationApi api;
    @Resource private DeliverableChecklistMapper deliverableMapper;
    @Resource private PlatformTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void initialize_withoutCallerTransaction_isRejected() {
        assertThrows(IllegalTransactionStateException.class, () -> api.initialize(commandWithTwoRequirements()));
    }

    @Test
    void initialize_createsAllRequirementsInCallerTransaction() {
        DeliverableInitializationResult result = transactionTemplate.execute(
                status -> api.initialize(commandWithTwoRequirements()));

        assertEquals(2, result.createdCount());
        assertEquals(2, result.deliverableIds().size());
        assertEquals(2L, deliverableMapper.selectCount(null));
        List<DeliverableChecklistDO> rows = deliverableMapper.selectList();
        assertEquals(List.of("PENDING"), rows.stream().map(DeliverableChecklistDO::getStatus).distinct().toList());
        assertEquals(List.of(900L), rows.stream().map(DeliverableChecklistDO::getSourceTemplateRevisionId)
                .distinct().toList());
    }

    @Test
    void initialize_secondInsertFailure_rollsBackFirstInsert() {
        DeliverableInitializationCommand command = new DeliverableInitializationCommand(
                1L, 100L, 900L, List.of(requirement("D1"), requirement("FAIL")));

        assertThrows(RuntimeException.class,
                () -> transactionTemplate.executeWithoutResult(status -> api.initialize(command)));

        assertEquals(0L, deliverableMapper.selectCount(null));
    }

    @Test
    void initialize_replayReturnsExistingIdsWithoutDuplicates() {
        DeliverableInitializationResult first = transactionTemplate.execute(
                status -> api.initialize(commandWithTwoRequirements()));
        DeliverableInitializationResult replay = transactionTemplate.execute(
                status -> api.initialize(commandWithTwoRequirements()));

        assertEquals(first.deliverableIds(), replay.deliverableIds());
        assertEquals(2L, deliverableMapper.selectCount(null));
    }

    private DeliverableInitializationCommand commandWithTwoRequirements() {
        return new DeliverableInitializationCommand(1L, 100L, 900L,
                List.of(requirement("D1"), requirement("D2")));
    }

    private DeliverableRequirementSnapshot requirement(String key) {
        return new DeliverableRequirementSnapshot(key, null, "REQUIRED", "S0", true);
    }
}
