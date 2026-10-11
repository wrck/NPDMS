package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectContactRuleWakeupTest {
    @Test void durableWakeupRollsBackWithContactCommandAndCarriesOnlyProjectIdentity() {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:contactWakeup;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(dataSource); jdbc.execute("CREATE TABLE contact_wakeup(payload VARCHAR(4000))");
        var outbox=mock(PlatformBusinessEventApi.class);
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            BusinessEvent event=call.getArgument(2); assertEquals("ProjectRuleReevaluationRequested",event.eventType());
            var payload=JsonUtils.parseTree(event.eventPayload()); assertEquals(7L,payload.path("projectId").asLong());
            assertEquals(1L,payload.path("tenantId").asLong()); assertFalse(payload.has("mobile"));
            jdbc.update("INSERT INTO contact_wakeup VALUES (?)",event.eventPayload()); return null;
        }).when(outbox).append(anyString(),anyString(),any());
        var service=new ProjectContactRuleWakeup(outbox);
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThrows(IllegalStateException.class,()->transaction.execute(status->{service.changed(1L,7L,3L); throw new IllegalStateException("rollback");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM contact_wakeup",Integer.class));
        transaction.execute(status->{service.changed(1L,7L,3L); return null;});
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM contact_wakeup",Integer.class));
    }
}
