package cn.iocoder.yudao.module.pms.platform.service.outbox;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.PlatformOutboxDeliveryMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.DueOutboxListQuery;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Production SQL and transaction proxy; the runner owns a disposable, uniquely named schema. */
@EnabledIfEnvironmentVariable(named = "OUTBOX_CONCURRENCY_JDBC_URL", matches = ".+")
class PlatformOutboxClaimConcurrencyMySqlTest {
    @Test
    void unrelatedEventTypesRemainClaimableWhileMatchingRowsStayLocked() throws Exception {
        String url = System.getenv("OUTBOX_CONCURRENCY_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/npdms_outbox_race_[a-f0-9]+\\?.*"));
        var dataSource = new DriverManagerDataSource(url,
                System.getenv("OUTBOX_CONCURRENCY_DB_USER"), System.getenv("OUTBOX_CONCURRENCY_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE plt_outbox_event (
                  id BIGINT PRIMARY KEY, event_id VARCHAR(80), event_type VARCHAR(128),
                  aggregate_type VARCHAR(64), aggregate_key VARCHAR(64), payload TEXT,
                  status VARCHAR(32), occurred_at DATETIME, next_retry_time DATETIME NULL,
                  retry_count INT, tenant_id BIGINT, creator VARCHAR(64), create_time DATETIME,
                  updater VARCHAR(64), update_time DATETIME,
                  KEY idx_plt_outbox_dispatch(status,next_retry_time,id)
                ) ENGINE=InnoDB
                """);
        for (int id = 1; id <= 100; id++) {
            jdbc.update("""
                    INSERT INTO plt_outbox_event(id,event_id,event_type,status,occurred_at,retry_count,tenant_id)
                    VALUES(?,?,?,'PENDING','2026-10-01 00:00:00',0,7)
                    """, id, "race-" + id, id % 2 == 0 ? "FileReferenceAttached" : "ProjectRuleReevaluationRequested");
        }
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("outbox-race", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(PlatformOutboxDeliveryMapper.class);
        String resource = "mapper/outbox/PlatformOutboxDeliveryMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var sql = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
        var realMapper = sql.getMapper(PlatformOutboxDeliveryMapper.class);
        var mapper = mock(PlatformOutboxDeliveryMapper.class);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            DueOutboxListQuery query = call.getArgument(0);
            var rows = realMapper.selectDueForUpdate(query);
            if (Thread.currentThread().getName().equals("outbox-rule-claim")) {
                acquired.countDown();
                assertTrue(release.await(15, TimeUnit.SECONDS), "Concurrent claim did not finish");
            }
            return rows;
        }).when(mapper).selectDueForUpdate(any());
        var target = new PlatformOutboxDeliveryApiImpl();
        ReflectionTestUtils.setField(target, "mapper", mapper);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        var api = (PlatformOutboxDeliveryApi) proxy.getProxy();
        var rules = new PlatformOutboxClaimQuery(LocalDateTime.now(), 50, Set.of("ProjectRuleReevaluationRequested"));
        try (var executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "outbox-rule-claim"))) {
            var first = executor.submit(() -> {
                TenantContextHolder.setTenantId(7L);
                try { return api.claimDue(rules); }
                finally { TenantContextHolder.clear(); }
            });
            try {
                assertTrue(acquired.await(10, TimeUnit.SECONDS));
                TenantContextHolder.setTenantId(7L);
                var files = api.claimDue(new PlatformOutboxClaimQuery(LocalDateTime.now(), 50,
                        Set.of("FileReferenceAttached")));
                assertEquals(50, files.size(), "Rule scan must not hide unrelated pending file events");
                assertTrue(files.stream().allMatch(row -> row.tenantId() == 7L
                        && row.eventType().equals("FileReferenceAttached")));
                assertTrue(api.claimDue(rules).isEmpty(), "Matching locked events must still be skipped");
                TenantContextHolder.setTenantId(8L);
                assertTrue(api.claimDue(rules).isEmpty(), "Tenant boundary must remain effective");
            } finally {
                release.countDown();
                TenantContextHolder.clear();
            }
            assertEquals(50, first.get(10, TimeUnit.SECONDS).size());
        }
    }
}
