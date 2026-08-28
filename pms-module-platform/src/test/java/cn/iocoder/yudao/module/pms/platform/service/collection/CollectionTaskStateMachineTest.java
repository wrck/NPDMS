package cn.iocoder.yudao.module.pms.platform.service.collection;

import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.pms.platform.service.collection.CollectionTaskStateMachine.CompletionMode.BUSINESS_CONSUMPTION;
import static cn.iocoder.yudao.module.pms.platform.service.collection.CollectionTaskStateMachine.Status.CANCELLED;
import static cn.iocoder.yudao.module.pms.platform.service.collection.CollectionTaskStateMachine.Status.COMPLETED;
import static cn.iocoder.yudao.module.pms.platform.service.collection.CollectionTaskStateMachine.Status.FAILED;
import static cn.iocoder.yudao.module.pms.platform.service.collection.CollectionTaskStateMachine.Status.RESULT_AVAILABLE;
import static cn.iocoder.yudao.module.pms.platform.service.collection.CollectionTaskStateMachine.Status.SECURITY_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionTaskStateMachineTest {

    private final CollectionTaskStateMachine machine = new CollectionTaskStateMachine();

    @Test
    void technicalSuccessWaitsForMatchingBusinessConsumption() {
        CollectionTaskStateMachine.TaskSnapshot available = machine.onTechnicalSuccess(task(BUSINESS_CONSUMPTION));

        assertEquals(RESULT_AVAILABLE, available.status());
        assertEquals(COMPLETED, machine.onConsumed(available, consumer(1L)).status());
    }

    @Test
    void oldResultVersionCannotCompleteTask() {
        assertThrows(IllegalStateException.class,
                () -> machine.onConsumed(task(RESULT_AVAILABLE, 2L), consumer(1L)));
    }

    @Test
    void wrongConsumerObjectCannotCompleteTask() {
        CollectionTaskStateMachine.ConsumerConfirmation wrongConsumer =
                new CollectionTaskStateMachine.ConsumerConfirmation("IMP", "ConfigurationCollectionResult", "other", 1L);

        assertThrows(IllegalStateException.class,
                () -> machine.onConsumed(task(RESULT_AVAILABLE, 1L), wrongConsumer));
    }

    @Test
    void terminalFailureCannotRecoverInPlace() {
        assertThrows(IllegalStateException.class, () -> machine.onTechnicalSuccess(task(FAILED, 1L)));
        assertThrows(IllegalStateException.class, () -> machine.onTechnicalSuccess(task(CANCELLED, 1L)));
        assertThrows(IllegalStateException.class, () -> machine.onTechnicalSuccess(task(SECURITY_EXCEPTION, 1L)));
    }

    @Test
    void partialSuccessProducesAvailableResultAndRetainsClassification() {
        CollectionTaskStateMachine.TaskSnapshot available = machine.onExternalTerminal(
                task(BUSINESS_CONSUMPTION), "PARTIAL_SUCCESS", 1L);

        assertEquals(RESULT_AVAILABLE, available.status());
        assertEquals("PARTIAL_SUCCESS", available.technicalResult());
    }

    private CollectionTaskStateMachine.TaskSnapshot task(CollectionTaskStateMachine.CompletionMode completionMode) {
        return new CollectionTaskStateMachine.TaskSnapshot(
                CollectionTaskStateMachine.Status.CALLBACK_PROCESSING,
                completionMode,
                1L,
                "IMP",
                "ConfigurationCollectionResult",
                "result-1",
                null);
    }

    private CollectionTaskStateMachine.TaskSnapshot task(CollectionTaskStateMachine.Status status, Long resultVersion) {
        return new CollectionTaskStateMachine.TaskSnapshot(
                status,
                BUSINESS_CONSUMPTION,
                resultVersion,
                "IMP",
                "ConfigurationCollectionResult",
                "result-1",
                null);
    }

    private CollectionTaskStateMachine.ConsumerConfirmation consumer(Long resultVersion) {
        return new CollectionTaskStateMachine.ConsumerConfirmation(
                "IMP", "ConfigurationCollectionResult", "result-1", resultVersion);
    }
}
