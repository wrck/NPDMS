package cn.iocoder.yudao.module.pms.platform.service.command;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformBusinessEventTransactionTest {
    @Test void eventMustJoinOwnerTransactionAndRollBackWithOwnerFailure() {
        // In-memory H2 only: no application configuration, Docker connection or production database.
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE owner_result (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE outbox_result (event_id VARCHAR(64) PRIMARY KEY, tenant_id BIGINT)");
            var mapper = mock(PlatformOutboxEventMapper.class);
            when(mapper.insert(any(PlatformOutboxEventDO.class))).thenAnswer(call -> {
                PlatformOutboxEventDO row = call.getArgument(0);
                return jdbc.update("INSERT INTO outbox_result VALUES (?,?)", row.getEventId(), row.getTenantId());
            });
            var manager = new DataSourceTransactionManager(database);
            var factory = new ProxyFactory(new PlatformTransactionalOutboxWriter(mapper));
            factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            var events = (PlatformBusinessEventApi) factory.getProxy();
            var event = new BusinessEvent("rule-event-1", "ProjectRuleReevaluationRequested", "{\"eventId\":\"rule-event-1\"}");
            TenantContextHolder.setTenantId(7L);
            assertThrows(IllegalTransactionStateException.class, () -> events.append("Owner", "1", event));
            var transaction = new TransactionTemplate(manager);
            assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO owner_result VALUES (1)");
                events.append("Owner", "1", event);
                throw new IllegalStateException("Owner failed before commit");
            }));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM owner_result", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_result", Integer.class));
            transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO owner_result VALUES (1)");
                events.append("Owner", "1", event);
            });
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM owner_result", Integer.class));
            assertEquals(7L, jdbc.queryForObject("SELECT tenant_id FROM outbox_result", Long.class));
        } finally { TenantContextHolder.clear(); database.shutdown(); }
    }
}
