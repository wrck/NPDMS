package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.support.controller.DefaultBusinessController;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** The inherited HTTP receipt endpoint must establish the same service transaction as execution. */
class DefaultBusinessReceiptTransactionTest {
    static class CallerReached extends RuntimeException { }
    static class Transactions extends AbstractPlatformTransactionManager {
        int begins, rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) { begins++; }
        protected void doCommit(DefaultTransactionStatus status) { }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
    @Test void inheritedReceiptEndpointOwnsItsTransactionAndRollsBackFailure() {
        var manager = new Transactions();
        BusinessCallerContext caller = () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            throw new CallerReached();
        };
        var defaults = new DefaultBusinessApplicationService(caller, null, null, null, null, null, null,
                new TransactionTemplate(manager));
        var extension = new ExtensibleBusinessApplicationService(defaults) { };
        var controller = new DefaultBusinessController(extension, "IT", "note") { };
        assertThrows(CallerReached.class, () -> controller.receipt("save", 1, "existing-intent"));
        assertEquals(1, manager.begins);
        assertEquals(1, manager.rollbacks);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
}
