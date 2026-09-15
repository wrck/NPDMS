package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRuleCommittedEventListenerTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {
        @Bean org.springframework.jdbc.datasource.embedded.EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean DataSourceTransactionManager transactionManager(javax.sql.DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Test void committedEventRunsImmediatelyOnIndependentTransactionWithoutQuartzAndRollbackDoesNotRun() throws Exception {
        var delivery = mock(ProjectRuleOutboxDeliveryJob.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("projectRuleOutboxDeliveryJob", delivery);
            context.register(Transactions.class, ProjectRuleEventConfiguration.class, ProjectRuleCommittedEventListener.class);
            context.refresh();
            var manager = context.getBean(DataSourceTransactionManager.class);
            var jdbc = new JdbcTemplate(manager.getDataSource());
            jdbc.execute("CREATE TABLE owner_fact (id INT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE execution_fact (id INT PRIMARY KEY)");
            var transaction = new TransactionTemplate(manager);
            var done = new CountDownLatch(1);
            var failure = new AtomicReference<Throwable>();
            when(delivery.deliver(any(), any())).thenAnswer(call -> {
                try {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(7L, TenantContextHolder.getRequiredTenantId());
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM owner_fact", Integer.class));
                    transaction.executeWithoutResult(status -> jdbc.update("INSERT INTO execution_fact VALUES (1)"));
                } catch (Throwable error) { failure.set(error); }
                finally { done.countDown(); }
                return true;
            });
            context.publishEvent(event("outside", ProjectRuleReevaluation.EVENT_TYPE, null));
            assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO owner_fact VALUES (1)");
                context.publishEvent(event("rolled-back", ProjectRuleReevaluation.EVENT_TYPE, null));
                throw new IllegalStateException("Owner rollback");
            }));
            verifyNoInteractions(delivery);
            transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO owner_fact VALUES (1)");
                context.publishEvent(event("committed", ProjectRuleReevaluation.EVENT_TYPE, null));
                verifyNoInteractions(delivery);
            });
            assertTrue(done.await(5, TimeUnit.SECONDS), "Event must not wait for a Quartz tick");
            assertNull(failure.get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM execution_fact", Integer.class));
            verify(delivery).deliver(argThat(message -> message.eventId().equals("committed")), any());
            verifyNoMoreInteractions(delivery);
        }
    }

    @Test void notificationsAndFutureTimersDoNotExecuteEarly() {
        var delivery = mock(ProjectRuleOutboxDeliveryJob.class);
        var listener = new ProjectRuleCommittedEventListener(delivery, Runnable::run);
        listener.onCommitted(event("notification", "TaskCompleted", null));
        listener.onCommitted(event("timer", ProjectRuleTimer.EVENT_TYPE, null));
        listener.onCommitted(event("future", ProjectRuleReevaluation.EVENT_TYPE, LocalDateTime.now().plusHours(1)));
        verifyNoInteractions(delivery);
    }

    @Test void dispatchAndConsumerFailureDoNotTurnCommittedBusinessIntoAFailedRequest() {
        var delivery = mock(ProjectRuleOutboxDeliveryJob.class);
        var message = event("committed", ProjectRuleReevaluation.EVENT_TYPE, null);
        var rejected = new ProjectRuleCommittedEventListener(delivery, task -> { throw new TaskRejectedException("full"); });
        assertDoesNotThrow(() -> rejected.onCommitted(message));
        verifyNoInteractions(delivery);
        when(delivery.deliver(any(), any())).thenThrow(new IllegalStateException("Owner private error"));
        TenantContextHolder.setTenantId(99L);
        try {
            assertDoesNotThrow(() -> new ProjectRuleCommittedEventListener(delivery, Runnable::run).onCommitted(message));
            assertEquals(99L, TenantContextHolder.getRequiredTenantId());
        } finally { TenantContextHolder.clear(); }
    }

    @Test void parallelEventsKeepTheirOwnTenantAndDoNotWaitForABatch() throws Exception {
        var delivery = mock(ProjectRuleOutboxDeliveryJob.class);
        var executor = new ProjectRuleEventConfiguration().projectRuleEventExecutor();
        executor.initialize();
        var done = new CountDownLatch(40);
        var failure = new AtomicReference<Throwable>();
        when(delivery.deliver(any(), any())).thenAnswer(call -> {
            try {
                PlatformOutboxMessageDTO message = call.getArgument(0);
                assertEquals(message.tenantId(), TenantContextHolder.getRequiredTenantId());
                assertFalse(TenantContextHolder.isIgnore());
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
            return true;
        });
        try {
            var listener = new ProjectRuleCommittedEventListener(delivery, executor);
            for (long tenant = 1; tenant <= 40; tenant++) {
                var base = event("event-" + tenant, ProjectRuleReevaluation.EVENT_TYPE, null).message();
                listener.onCommitted(new PlatformOutboxAppended(new PlatformOutboxMessageDTO(base.eventId(),
                        base.eventType(), base.payload(), 0, tenant, base.occurredAt()), null));
            }
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
            verify(delivery, times(40)).deliver(any(), any());
        } finally { executor.shutdown(); }
    }

    private static PlatformOutboxAppended event(String id, String type, LocalDateTime due) {
        return new PlatformOutboxAppended(new PlatformOutboxMessageDTO(id, type,
                "{\"eventId\":\"" + id + "\"}", 0, 7L, LocalDateTime.now()), due);
    }
}
