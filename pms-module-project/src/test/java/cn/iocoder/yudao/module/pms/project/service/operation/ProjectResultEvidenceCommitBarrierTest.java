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

/** 真实提交通道、业务行与Spring事务；H2仅验证屏障协议，不代替MySQL验收。 */
class ProjectResultEvidenceCommitBarrierTest {
    private static final Type TYPE = new Type("TEST", "NATIVE", "COMPLETED");
    private final AtomicReference<Observation> observation = new AtomicReference<>();
    private final AtomicInteger reads = new AtomicInteger();
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ProjectBusinessResultJournal journal;
    private boolean failOutbox;
    private ProjectBusinessResultSources sources;
    private ProjectResultEvidenceConsistency consistency;
    private cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO subscription;

    @BeforeEach void setUp() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:result_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        // Only SQL dialect/schema details are adapted for the embedded test database.
        jdbc.execute("CREATE TABLE proj_business_result_channel(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,project_id BIGINT NOT NULL,owner_context VARCHAR(128) NOT NULL,entity_type VARCHAR(128) NOT NULL,result_type VARCHAR(128) NOT NULL,committed_sequence BIGINT NOT NULL DEFAULT 0,deleted TINYINT DEFAULT 0,UNIQUE(tenant_id,project_id,owner_context,entity_type,result_type))");
        jdbc.execute("CREATE TABLE proj_business_result_change(tenant_id BIGINT,channel_id BIGINT,sequence_no BIGINT,source_event_id VARCHAR(64),notification_id VARCHAR(64),object_id VARCHAR(128),result_id VARCHAR(128),formation_marker TINYINT,payload CLOB,deleted TINYINT DEFAULT 0,PRIMARY KEY(channel_id,sequence_no),UNIQUE(channel_id,source_event_id),UNIQUE(channel_id,object_id,result_id,formation_marker))");
        jdbc.execute("CREATE TABLE test_owner(id BIGINT PRIMARY KEY, state VARCHAR(32))");
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
            public boolean transactionalChangeCoverage(){return true;}
            public Observation inspect(Query query){
                reads.incrementAndGet();
                String state=jdbc.queryForObject("SELECT state FROM test_owner WHERE id=100",String.class);
                return Observation.available(new Result(1L,3L,TYPE,"100","40","1",state,Validity.valueOf(state),LocalDateTime.of(2026,9,18,1,0)));
            }
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
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        sources=new ProjectBusinessResultSources(List.of(source));
        var target=new ProjectBusinessResultJournal(sessions.getMapper(BusinessResultJournalMapper.class),sources,outbox);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        journal=(ProjectBusinessResultJournal)proxy.getProxy();
        jdbc.update("INSERT INTO test_owner VALUES(100,'CURRENT')");
        var boundary=tx.execute(status->journal.capture(1L,3L,TYPE));
        subscription=new cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultSubscriptionDO();
        subscription.setTenantId(1L);subscription.setProjectId(3L);subscription.setChannelId(boundary.channel().id());
        subscription.setConfiguration("{\"key\":\"result\",\"ownerContext\":\"TEST\",\"entityType\":\"NATIVE\",\"resultType\":\"COMPLETED\",\"scope\":{\"mode\":\"PROJECT\"},\"policy\":{\"acquisition\":\"REUSE_EXISTING\",\"validity\":\"CURRENT_VALID\",\"selection\":\"ANY_MATCHING\"}}");
        var barrierProxy=new ProxyFactory(new ProjectResultEvidenceConsistency(sources,journal));barrierProxy.setProxyTargetClass(true);
        barrierProxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        consistency=(ProjectResultEvidenceConsistency)barrierProxy.getProxy();
    }
    @AfterEach void clean(){TenantContextHolder.clear();jdbc.execute("SHUTDOWN");}

    @Test void channelBarrierExcludesUncommittedOwnerMutationWithoutTakingItsRowLock() throws Exception {
        var barrierLocked=new CountDownLatch(1);var ownerChanged=new CountDownLatch(1);var observed=new CountDownLatch(1);var release=new CountDownLatch(1);
        var ownerResult=new AtomicReference<Observation>();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var scan=pool.submit(()->{TenantContextHolder.setTenantId(1L);try{return tx.execute(status->{
                var boundary=consistency.lock(subscription);barrierLocked.countDown();await(ownerChanged);
                ownerResult.set(sources.inspect(new Query(1L,3L,TYPE,"100","40")));observed.countDown();await(release);return boundary;
            });}finally{TenantContextHolder.clear();}});
            assertTrue(barrierLocked.await(3,TimeUnit.SECONDS));
            var writer=pool.submit(()->{TenantContextHolder.setTenantId(1L);try{tx.executeWithoutResult(status->{
                jdbc.update("UPDATE test_owner SET state='REVOKED' WHERE id=100");ownerChanged.countDown();journal.record(event());
            });}finally{TenantContextHolder.clear();}});
            try {
                assertTrue(observed.await(3,TimeUnit.SECONDS));
                assertEquals(Validity.CURRENT,ownerResult.get().result().validity());
                assertThrows(TimeoutException.class,()->writer.get(100,TimeUnit.MILLISECONDS));
            } finally {release.countDown();}
            assertEquals(0,scan.get(3,TimeUnit.SECONDS).sequence());writer.get(3,TimeUnit.SECONDS);
            assertEquals(1,tx.execute(status->consistency.lock(subscription)).sequence());
            assertEquals(Validity.REVOKED,sources.inspect(new Query(1L,3L,TYPE,"100","40")).result().validity());
        } finally {release.countDown();}
    }

    @Test void failedNativeTransactionCannotChangeTheSequenceOrValidatedResult() {
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            jdbc.update("UPDATE test_owner SET state='REVOKED' WHERE id=100");journal.record(event());throw new IllegalStateException("POST_FAILED");
        }));
        assertEquals(0,tx.execute(status->consistency.lock(subscription)).sequence());
        assertEquals(Validity.CURRENT,sources.inspect(new Query(1L,3L,TYPE,"100","40")).result().validity());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM test_outbox",Integer.class));
    }

    @Test void ownerCommitBetweenEvidencePagesChangesTheBoundary() {
        var first=tx.execute(status->consistency.lock(subscription));
        tx.executeWithoutResult(status->{jdbc.update("UPDATE test_owner SET state='NOT_CURRENT' WHERE id=100");journal.record(event());});
        var second=tx.execute(status->consistency.lock(subscription));
        assertEquals(first.channel(),second.channel());assertEquals(first.sequence()+1,second.sequence());
        assertEquals(Validity.NOT_CURRENT,sources.inspect(new Query(1L,3L,TYPE,"100","40")).result().validity());
    }

    @Test void evidenceBarrierMustJoinTheCallerTransaction() {
        assertThrows(IllegalTransactionStateException.class,()->consistency.lock(subscription));
    }
    private BusinessOperationResultEvent event(){return new BusinessOperationResultEvent(UUID.randomUUID().toString(),1,1L,3L,"TEST","NATIVE","100","40",2,"fact:40","STATUS_CHANGED","UPDATE","key",9L,LocalDateTime.of(2026,9,18,1,0),"trace");}
    private static void await(CountDownLatch latch){try{if(!latch.await(3,TimeUnit.SECONDS))throw new IllegalStateException("TEST_LOCK_TIMEOUT");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}}
}
