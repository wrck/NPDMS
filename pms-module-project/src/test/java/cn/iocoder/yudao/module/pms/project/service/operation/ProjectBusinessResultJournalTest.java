package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.BusinessResultJournalMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Real Spring transaction proxy and production Mapper XML on H2; not a MySQL or real Owner/Outbox integration. */
class ProjectBusinessResultJournalTest {
    private static final Type TYPE = new Type("TEST", "NATIVE", "COMPLETED");
    private final AtomicReference<Observation> observation = new AtomicReference<>();
    private final AtomicInteger reads = new AtomicInteger();
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ProjectBusinessResultJournal journal;
    private boolean failOutbox;

    @BeforeEach void setUp() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:result_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        // Only SQL dialect/schema details are adapted for the embedded test database.
        jdbc.execute("CREATE TABLE proj_business_result_channel(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,project_id BIGINT NOT NULL,owner_context VARCHAR(128) NOT NULL,entity_type VARCHAR(128) NOT NULL,result_type VARCHAR(128) NOT NULL,committed_sequence BIGINT NOT NULL DEFAULT 0,deleted TINYINT DEFAULT 0,UNIQUE(tenant_id,project_id,owner_context,entity_type,result_type))");
        jdbc.execute("CREATE TABLE proj_business_result_change(tenant_id BIGINT,channel_id BIGINT,sequence_no BIGINT,source_event_id VARCHAR(64),notification_id VARCHAR(64),object_id VARCHAR(128),result_id VARCHAR(128),formation_marker TINYINT,payload CLOB,deleted TINYINT DEFAULT 0,PRIMARY KEY(channel_id,sequence_no),UNIQUE(channel_id,source_event_id),UNIQUE(channel_id,object_id,result_id,formation_marker))");
        jdbc.execute("CREATE TABLE test_owner(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE test_outbox(event_id VARCHAR(64) PRIMARY KEY,payload CLOB)");
        var configuration = new Configuration(new Environment("embedded-result",new SpringManagedTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource="mapper/businessresult/BusinessResultJournalMapper.xml";
        try (var xml=getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(xml); new XMLMapperBuilder(xml,configuration,resource,configuration.getSqlFragments()).parse();
        }
        var sessions=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        BusinessResultChangeSource source=new BusinessResultChangeSource() {
            public Descriptor descriptor(){return new Descriptor(TYPE,true,true,true);}
            public Query changeQuery(BusinessOperationResultEvent event){return new Query(event.tenantId(),event.projectId(),TYPE,event.objectId(),event.revisionId());}
            public Observation inspect(Query query){reads.incrementAndGet();return observation.get();}
        };
        PlatformBusinessEventApi outbox=new PlatformBusinessEventApi() {
            public void append(String aggregateType,String aggregateKey,BusinessEvent event){
                if(failOutbox) throw new IllegalStateException("OUTBOX_UNAVAILABLE");
                jdbc.update("INSERT INTO test_outbox VALUES(?,?)",event.eventId(),event.eventPayload());
            }
            public void appendAt(String a,String k,BusinessEvent event,LocalDateTime at){append(a,k,event);}
        };
        var manager=new DataSourceTransactionManager(dataSource);
        tx=new TransactionTemplate(manager);
        var target=new ProjectBusinessResultJournal(sessions.getMapper(BusinessResultJournalMapper.class),new ProjectBusinessResultSources(List.of(source)),outbox);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        journal=(ProjectBusinessResultJournal)proxy.getProxy();
        formed("40");
    }
    @AfterEach void clean(){TenantContextHolder.clear();jdbc.execute("SHUTDOWN");}

    @Test void recordingAndBoundaryCaptureCannotStartAnIndependentTransaction(){
        assertThrows(IllegalTransactionStateException.class,()->journal.record(event("40")));
        assertThrows(IllegalTransactionStateException.class,()->journal.capture(1L,3L,TYPE));
        assertEquals(0,count("proj_business_result_channel"));
    }
    @Test void writesNativeIdentityAndNotificationInsideTheOriginalTransaction(){
        var input=event("40"); tx.executeWithoutResult(status->{jdbc.update("INSERT INTO test_owner VALUES(1)");journal.record(input);});
        var boundary=capture(); assertEquals(1,boundary.sequence());
        var page=journal.read(boundary,0,10); var change=page.changes().getFirst();
        assertTrue(change.formation()); assertEquals(input,change.source()); assertEquals("40",change.observation().result().resultId());
        assertEquals(1,count("test_owner")); assertEquals(1,count("test_outbox")); assertTrue(page.complete());
        assertEquals(change.eventId(),JsonUtils.parseObject(jdbc.queryForObject("SELECT payload FROM test_outbox",String.class),BusinessResultChange.class).eventId());
    }
    @ParameterizedTest @ValueSource(strings={"after-record","outbox"})
    void ownerJournalSequenceAndOutboxRollbackTogether(String failure){
        var start=capture(); failOutbox=failure.equals("outbox");
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            jdbc.update("INSERT INTO test_owner VALUES(1)");journal.record(event("40"));throw new IllegalStateException("OWNER_POST_FAILED");
        }));
        assertEquals(0,count("test_owner")); assertEquals(0,count("test_outbox"));assertEquals(0,count("proj_business_result_change"));
        assertEquals(start,capture()); failOutbox=false;
        tx.executeWithoutResult(status->journal.record(event("40")));assertEquals(1,capture().sequence());
    }
    @Test void replayDoesNotRereadMutableOwnerFactsOrAppendADuplicate(){
        var input=event("40");tx.executeWithoutResult(status->journal.record(input));
        observation.set(Observation.absent(Status.UNAVAILABLE,"REPLACED_LATER"));
        tx.executeWithoutResult(status->journal.record(input));assertEquals(1,reads.get());assertEquals(1,capture().sequence());assertEquals(1,count("test_outbox"));
        var conflict=new BusinessOperationResultEvent(input.eventId(),1,1L,3L,"TEST","NATIVE","100","41",2,"changed", "COMPLETED","CONFIRM","key",9L,input.occurredAt(),"trace");
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->journal.record(conflict)));assertEquals(1,reads.get());
    }
    @Test void freshEventIdsCannotMakeTheSameNativeResultNewAgain(){
        tx.executeWithoutResult(status->journal.record(event("40")));tx.executeWithoutResult(status->journal.record(event("40")));
        var changes=journal.read(capture(),0,10).changes();assertEquals(2,changes.size());assertTrue(changes.getFirst().formation());assertFalse(changes.getLast().formation());
    }
    @Test void paginationRetainsItsOriginalUpperBoundAndDetectsMissingSequences(){
        for(String id:List.of("40","41","42")){formed(id);tx.executeWithoutResult(status->journal.record(event(id)));}
        var through=capture();var first=journal.read(through,0,2);assertEquals(2,first.nextSequence());assertFalse(first.complete());
        formed("43");tx.executeWithoutResult(status->journal.record(event("43")));
        var second=journal.read(through,first.nextSequence(),2);assertEquals(3,second.nextSequence());assertTrue(second.complete());assertEquals("42",second.changes().getFirst().observation().result().resultId());
        assertTrue(journal.read(through,3,2).changes().isEmpty());
        jdbc.update("DELETE FROM proj_business_result_change WHERE sequence_no=2");
        assertEquals("RESULT_CHANGE_GAP",assertThrows(IllegalStateException.class,()->journal.read(through,0,2)).getMessage());
    }
    @ParameterizedTest @ValueSource(strings={"missing","draft","unavailable","revoked"})
    void claimedFormationRequiresAnAvailableNonRevokedNativeResult(String state){
        if(state.equals("revoked"))observation.set(Observation.available(new Result(1L,3L,TYPE,"100","40","1","2",Validity.REVOKED,LocalDateTime.now())));
        else observation.set(Observation.absent(state.equals("missing")?Status.NOT_FOUND:state.equals("draft")?Status.NOT_FORMED:Status.UNAVAILABLE,"OWNER_REASON"));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->journal.record(event("40"))));assertEquals(0,count("proj_business_result_change"));assertEquals(0,count("test_outbox"));
    }
    @Test void unformedChangesAreObservationsNotSuccessfulResults(){
        observation.set(Observation.absent(Status.NOT_FORMED,"DRAFT"));
        var original=event("40");var draft=new BusinessOperationResultEvent(original.eventId(),1,1L,3L,"TEST","NATIVE","100","40",1,"draft","DRAFT_SAVED","SAVE","key",9L,original.occurredAt(),"trace");
        tx.executeWithoutResult(status->journal.record(draft));var change=journal.read(capture(),0,1).changes().getFirst();assertFalse(change.formation());assertNull(change.observation().result());
    }
    @Test void boundaryAndPageValidationCannotCrossTenantOrSilentlyTruncate(){
        var start=capture();
        for(int limit:new int[]{0,-1,201})assertThrows(IllegalArgumentException.class,()->journal.read(start,0,limit));
        assertThrows(IllegalArgumentException.class,()->journal.read(start,1,10));
        assertThrows(IllegalArgumentException.class,()->journal.read(start,-1,10));
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->journal.read(start,0,10));
        assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(status->journal.record(event("40"))));
    }
    @Test void channelLockOrdersCaptureAfterACommittedWriter() throws Exception {
        capture();var wrote=new CountDownLatch(1);var release=new CountDownLatch(1);var beganCapture=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var writer=pool.submit(()->{TenantContextHolder.setTenantId(1L);try{tx.executeWithoutResult(status->{journal.record(event("40"));wrote.countDown();await(release);});}finally{TenantContextHolder.clear();}});
            assertTrue(wrote.await(3,TimeUnit.SECONDS));
            var boundary=pool.submit(()->{TenantContextHolder.setTenantId(1L);try{beganCapture.countDown();return capture();}finally{TenantContextHolder.clear();}});
            assertTrue(beganCapture.await(3,TimeUnit.SECONDS));
            try{assertThrows(TimeoutException.class,()->boundary.get(150,TimeUnit.MILLISECONDS));}finally{release.countDown();}
            writer.get(3,TimeUnit.SECONDS);assertEquals(1,boundary.get(3,TimeUnit.SECONDS).sequence());
        } finally{release.countDown();}
    }
    @Test void damagedPayloadCannotMasqueradeAsAnotherChannelOrSequence(){
        tx.executeWithoutResult(status->journal.record(event("40")));var boundary=capture();
        jdbc.update("UPDATE proj_business_result_change SET notification_id='wrong'");
        assertEquals("RESULT_CHANGE_CORRUPT",assertThrows(IllegalStateException.class,()->journal.read(boundary,0,1)).getMessage());
    }
    @Test void replayUsesThePersistedTimestampPrecision() {
        var before=event("40");
        var event=new BusinessOperationResultEvent(before.eventId(),1,1L,3L,"TEST","NATIVE","100","40",2,
                "fact:40","COMPLETED","CONFIRM","key",9L,before.occurredAt().withNano(123456789),"trace");
        tx.executeWithoutResult(status->journal.record(event));tx.executeWithoutResult(status->journal.record(event));
        assertEquals(1,reads.get());assertEquals(1,count("test_outbox"));
    }
    @ParameterizedTest @ValueSource(strings={"string-version","missing-version","unknown-version","missing-formation"})
    void storedFormatCannotDefaultOrCoerceItsVersion(String damage) {
        tx.executeWithoutResult(status->journal.record(event("40")));var through=capture();
        var json=(tools.jackson.databind.node.ObjectNode)JsonUtils.parseTree(jdbc.queryForObject("SELECT payload FROM proj_business_result_change",String.class));
        if(damage.equals("string-version")) json.put("eventVersion","1");
        else if(damage.equals("missing-version")) json.remove("eventVersion");
        else if(damage.equals("unknown-version")) json.put("eventVersion",2);
        else json.remove("formation");
        jdbc.update("UPDATE proj_business_result_change SET payload=?",json.toString());
        assertEquals("RESULT_CHANGE_FORMAT_UNSUPPORTED",assertThrows(IllegalStateException.class,()->journal.read(through,0,1)).getMessage());
    }
    @Test void newlyFrozenNativeHistoryCanPrecedeActivationWithoutLosingItsFormationBoundary() {
        observation.set(Observation.available(new Result(1L,3L,TYPE,"100","40","1","2",Validity.NOT_CURRENT,LocalDateTime.now())));
        tx.executeWithoutResult(status->journal.record(event("40")));
        assertTrue(journal.read(capture(),0,1).changes().getFirst().formation());
    }
    private ProjectBusinessResultJournal.Boundary capture(){return tx.execute(status->journal.capture(1L,3L,TYPE));}
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private void formed(String id){observation.set(Observation.available(new Result(1L,3L,TYPE,"100",id,"1","2",Validity.CURRENT,LocalDateTime.of(2026,9,18,1,0))));}
    private BusinessOperationResultEvent event(String id){return new BusinessOperationResultEvent(UUID.randomUUID().toString(),1,1L,3L,"TEST","NATIVE","100",id,2,"fact:"+id,"COMPLETED","CONFIRM","key",9L,LocalDateTime.of(2026,9,18,1,0),"trace");}
    private static void await(CountDownLatch latch){try{if(!latch.await(3,TimeUnit.SECONDS))throw new IllegalStateException("TEST_LOCK_TIMEOUT");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}
}
