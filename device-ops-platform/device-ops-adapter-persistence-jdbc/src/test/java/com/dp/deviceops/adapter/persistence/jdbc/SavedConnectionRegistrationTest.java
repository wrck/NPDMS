package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.model.*;
import com.dp.deviceops.core.port.*;
import com.dp.deviceops.core.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SavedConnectionRegistrationTest {
    JdbcClient jdbc;
    JdbcSavedConnectionStore store;
    SavedConnectionService service;
    org.h2.jdbcx.JdbcConnectionPool pool;
    @BeforeEach void setup() {
        pool=org.h2.jdbcx.JdbcConnectionPool.create("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var ds=pool;
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=JdbcClient.create(ds);
        var cipher=new AesGcmCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        store=new JdbcSavedConnectionStore(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),new JdbcCredentialStore(jdbc,cipher,Clock.systemUTC()),Clock.systemUTC());
        var transport=new CommandExecutionPort(){
            public void test(ConnectionSpec c,char[] secret,char[] passphrase){if(!Arrays.equals("valid-test-password".toCharArray(),secret))throw new IllegalArgumentException("failed");}
            public CommandResult execute(ConnectionSpec c,char[] secret,char[] passphrase,String script,Duration timeout){throw new AssertionError("save must not execute commands");}
        };
        service=new SavedConnectionService(store,new ConnectionTestService(transport));
    }
    @AfterEach void close(){pool.dispose();}
    @Test void verifiedRegistrationReplaysWithoutChangingSecretAndKeepsTenantOwnerIsolation() {
        var first=save("valid-test-password");assertTrue(first.saved());
        assertEquals("stable-connection",first.connection().id());
        assertTrue(save("different-on-replay").saved());
        assertEquals(1,jdbc.sql("select count(*) from device_ops_saved_connection").query(Integer.class).single());
        assertEquals(1,jdbc.sql("select count(*) from device_ops_credential").query(Integer.class).single());
        store.withConnection("npdms","npdms-1","stable-connection",(metadata,credential)->credential.withCredentials((secret,passphrase)->{assertArrayEquals("valid-test-password".toCharArray(),secret);return null;}));
        assertTrue(store.find("other-owner","npdms-1","stable-connection").isEmpty());
        assertTrue(store.find("npdms","npdms-2","stable-connection").isEmpty());
        assertThrows(SavedConnectionStore.VersionConflictException.class,()->service.verifyAndCreateIdentified("npdms","npdms-1","stable-connection",draft("other.example"),new TransientCredential("valid-test-password".toCharArray(),null)));
    }
    @Test void failedProbeDoesNotPersistCredential() {
        assertFalse(save("wrong-password").saved());
        assertEquals(0,jdbc.sql("select count(*) from device_ops_credential").query(Integer.class).single());
    }
    @Test void concurrentRegistrationProducesOneConnectionAndOneSecret() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<SavedConnectionService.SaveResult> action=()->{gate.await();return save("valid-test-password");};
            var a=pool.submit(action);var b=pool.submit(action);gate.countDown();
            assertEquals(a.get(10,TimeUnit.SECONDS).connection().id(),b.get(10,TimeUnit.SECONDS).connection().id());
        }
        assertEquals(1,jdbc.sql("select count(*) from device_ops_saved_connection").query(Integer.class).single());
        assertEquals(1,jdbc.sql("select count(*) from device_ops_credential").query(Integer.class).single());
    }
    private SavedConnectionService.SaveResult save(String password){return service.verifyAndCreateIdentified("npdms","npdms-1","stable-connection",draft("device.example"),new TransientCredential(password.toCharArray(),null));}
    private SavedConnectionDraft draft(String host){return new SavedConnectionDraft("NPDMS connection",null,new CommandExecutionPort.ConnectionSpec(ConnectionProtocol.SSH2,host,22,"operator",CommandExecutionPort.AuthenticationType.PASSWORD,CommandExecutionPort.ExecutionMode.SHELL,null,null,Duration.ofSeconds(10)));}
}
