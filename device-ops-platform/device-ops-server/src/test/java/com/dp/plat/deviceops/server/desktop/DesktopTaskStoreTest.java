package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DesktopTaskStoreTest {
    @Test void taskIdentitySurvivesReopeningWhileCapabilityIsEncryptedAndOwnerIsImmutable() {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:desktop-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V25__desktop_task_capabilities.sql")).execute(datasource);
        var jdbc = JdbcClient.create(datasource);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        var store = new JdbcDesktopTaskStore(jdbc, tx, new AesGcmCredentialCipher(key));
        var registration = new JdbcDesktopTaskStore.Registration("npdms-1", "task-1", 1, "42", "7", "task-capability-for-tests-only-0123456789");
        var first = store.register(registration);
        assertFalse(new String(first.tokenCiphertext(), java.nio.charset.StandardCharsets.UTF_8).contains(registration.token()));
        var reopened = new JdbcDesktopTaskStore(jdbc, tx, new AesGcmCredentialCipher(key));
        var retry = reopened.register(registration);
        assertEquals(first.executionId(), retry.executionId());
        assertArrayEquals(registration.token().toCharArray(), reopened.token(retry));
        assertThrows(IllegalStateException.class, () -> reopened.register(new JdbcDesktopTaskStore.Registration("npdms-1", "task-1", 1, "43", "7", registration.token())));
        reopened.attach(new JdbcDesktopTaskStore.Attachment(retry.key(), "collection-1"));
        assertThrows(IllegalStateException.class, () -> reopened.attach(new JdbcDesktopTaskStore.Attachment(retry.key(), "collection-2")));
        assertEquals("collection-1", reopened.pending().getFirst().collectionId());
        reopened.polled(retry.key());
        reopened.acknowledge(retry.key());
        assertTrue(reopened.pending().isEmpty());
        jdbc.sql("UPDATE device_ops_desktop_task SET last_polled_at=:previous")
                .param("previous", java.time.Instant.now().minusSeconds(61)).update();
        for (int index=0;index<21;index++) reopened.register(new JdbcDesktopTaskStore.Registration(
                "npdms-1","pending-"+index,1,"42","7",registration.token()));
        assertEquals("collection-1", reopened.pending().getFirst().collectionId(), "completed tasks must still receive explicit redelivery requests");
    }
}
