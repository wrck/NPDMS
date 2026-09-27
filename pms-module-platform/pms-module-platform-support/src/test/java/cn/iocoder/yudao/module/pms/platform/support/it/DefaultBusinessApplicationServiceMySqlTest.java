package cn.iocoder.yudao.module.pms.platform.support.it;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventRecord;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessRelationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.AbstractBusinessApplicationService;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.pms.platform.support.service.DefaultBusinessApplicationService;
import cn.iocoder.yudao.module.pms.platform.support.service.OperationExecutionStore;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认业务应用服务真实 MySQL 集成测试：无专用 Service 的普通实体经分发器完成
 * 创建/保存、幂等重放与冲突、并发依据核对、CHANGED 事件与审计登记。
 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DefaultBusinessApplicationServiceMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DefaultBusinessApplicationServiceMySqlTest {

    @Resource private BusinessOperationDispatcher dispatcher;
    @Resource private RecordingEvents events;
    @Resource private RecordingAudit audit;
    @Resource private InMemoryExecutionStore executionStore;
    @Resource private JdbcTemplate jdbcTemplate;

    private long idBase;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        Map<String, String> values = environment();
        String port = values.getOrDefault("NPDMS_MYSQL_PORT", "25306");
        String database = values.getOrDefault("NPDMS_DB_NAME", "npdms_domain_test");
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", () -> required(values, "NPDMS_DB_USER"));
        registry.add("spring.datasource.password", () -> required(values, "NPDMS_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.druid.web-stat-filter.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.platform.support");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
    }

    @BeforeEach
    void setUp() {
        idBase = 772_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 100L;
        createTables();
        events.records.clear();
        audit.records.clear();
        executionStore.store.clear();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM pms_plat_it_order WHERE id >= ? AND id < ?", idBase, idBase + 100);
        jdbcTemplate.update("DELETE FROM pms_plat_it_checklist WHERE id >= ? AND id < ?", idBase, idBase + 100);
    }

    @Test
    void createsEntityThroughDispatcherWithoutSpecializedService() {
        BusinessOperationReceipt receipt = dispatcher.dispatch(createRequest("key-create-1", Map.of(
                "orderNo", "SO-NEW-1", "amount", new BigDecimal("12.50"), "status", "OPEN")));

        assertEquals(cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome.SAVED,
                receipt.outcome());
        assertNotNull(receipt.entityRef().entityId());
        assertEquals(0L, receipt.newConcurrencyBasis());
        assertEquals("SO-NEW-1", jdbcTemplate.queryForObject(
                "SELECT order_no FROM pms_plat_it_order WHERE id=?", String.class,
                receipt.entityRef().entityId()));

        assertEquals(1, events.records.size());
        assertEquals(BusinessEventKind.CHANGED, events.records.getFirst().kind());
        assertEquals("create", events.records.getFirst().payload().get("operation"));
        assertEquals(1, audit.records.size());
        assertEquals("create", audit.records.getFirst().operationCode());
    }

    @Test
    void replaysSameIntentReturningOriginalReceipt() {
        BusinessOperationRequest request = createRequest("key-replay-1", Map.of(
                "orderNo", "SO-REPLAY", "amount", new BigDecimal("1.00"), "status", "OPEN"));
        BusinessOperationReceipt first = dispatcher.dispatch(request);
        BusinessOperationReceipt second = dispatcher.dispatch(request);

        assertEquals(first, second);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pms_plat_it_order WHERE order_no='SO-REPLAY'", Integer.class));
        assertEquals(1, events.records.size());
    }

    @Test
    void rejectsDifferentPayloadUnderSameIdempotencyKey() {
        dispatcher.dispatch(createRequest("key-conflict-1", Map.of(
                "orderNo", "SO-CONF", "amount", new BigDecimal("1.00"), "status", "OPEN")));
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> dispatcher.dispatch(createRequest("key-conflict-1", Map.of(
                        "orderNo", "SO-CONF-OTHER", "amount", new BigDecimal("2.00"), "status", "OPEN"))));
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", ex.getErrorCode());
    }

    @Test
    void savesWithCurrentConcurrencyBasisAndIncrementsVersion() {
        BusinessOperationReceipt created = dispatcher.dispatch(createRequest("key-save-1", Map.of(
                "orderNo", "SO-SAVE", "amount", new BigDecimal("5.00"), "status", "OPEN")));

        BusinessOperationRequest save = new BusinessOperationRequest("save", 1,
                EntityDataRef.current(created.entityRef()), null, null,
                Map.of("status", "CLOSED"), "key-save-1-update", created.newConcurrencyBasis(),
                OperationEntryKind.INDEPENDENT, "corr-save-1");
        BusinessOperationReceipt saved = dispatcher.dispatch(save);

        assertEquals(cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.ReceiptOutcome.SAVED,
                saved.outcome());
        assertEquals(1L, saved.newConcurrencyBasis());
        assertEquals("CLOSED", jdbcTemplate.queryForObject(
                "SELECT order_status FROM pms_plat_it_order WHERE id=?", String.class,
                created.entityRef().entityId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT version FROM pms_plat_it_order WHERE id=?", Integer.class,
                created.entityRef().entityId()));
    }

    @Test
    void rejectsStaleConcurrencyBasis() {
        BusinessOperationReceipt created = dispatcher.dispatch(createRequest("key-stale-1", Map.of(
                "orderNo", "SO-STALE", "amount", new BigDecimal("5.00"), "status", "OPEN")));

        BusinessOperationRequest stale = new BusinessOperationRequest("save", 1,
                EntityDataRef.current(created.entityRef()), null, null,
                Map.of("status", "CLOSED"), "key-stale-2", created.newConcurrencyBasis() + 5,
                OperationEntryKind.INDEPENDENT, "corr-stale");
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> dispatcher.dispatch(stale));
        assertEquals("CONCURRENCY_CONFLICT", ex.getErrorCode());
    }

    @Test
    void doesNotAutoOpenUndeclaredOrNonDefaultOperations() {
        BusinessOperationRequest undeclared = createRequest("key-op-1", Map.of(
                "orderNo", "SO-OP", "amount", new BigDecimal("1.00"), "status", "OPEN"));
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> dispatcher.dispatch(new BusinessOperationRequest("approve", 1, null, "it", "order",
                        undeclared.input(), "key-op-approve", null,
                        OperationEntryKind.INDEPENDENT, "corr-op")));
        assertEquals("OPERATION_NOT_DECLARED", ex.getErrorCode());

        BusinessContractException fieldEx = assertThrows(BusinessContractException.class,
                () -> dispatcher.dispatch(createRequest("key-op-2", Map.of("secretField", "x"))));
        assertEquals("FIELD_NOT_WRITABLE", fieldEx.getErrorCode());
    }

    private BusinessOperationRequest createRequest(String idempotencyKey, Map<String, Object> input) {
        return new BusinessOperationRequest("create", 1, null, "it", "order", input,
                idempotencyKey, null, OperationEntryKind.INDEPENDENT, "corr-" + idempotencyKey);
    }

    private static Map<String, String> environment() {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path dotenv = findDotenv();
        if (dotenv == null) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#") || !value.contains("=")) {
                    continue;
                }
                int separator = value.indexOf('=');
                values.putIfAbsent(value.substring(0, separator).trim(),
                        unquote(value.substring(separator + 1).trim()));
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("读取真实MySQL集成测试环境失败", ex);
        }
    }

    private static Path findDotenv() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("compose.yaml"))) {
                return Files.isRegularFile(path.resolve(".env")) ? path.resolve(".env") : null;
            }
        }
        return null;
    }

    private static String unquote(String value) {
        return value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))
                ? value.substring(1, value.length() - 1) : value;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("真实MySQL集成测试缺少当前仓库参数：" + key);
        }
        return value;
    }

    private void createTables() {
        String auditColumns = "version bigint not null default 0, "
                + "creator varchar(64) default 'it', updater varchar(64) default 'it', "
                + "create_time datetime default current_timestamp, update_time datetime default current_timestamp "
                + "on update current_timestamp, deleted bit(1) not null default b'0', "
                + "tenant_id bigint not null";
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS pms_plat_it_order ("
                + "id bigint not null auto_increment primary key, order_no varchar(64), "
                + "amount decimal(18,2), order_status varchar(32), " + auditColumns + ") "
                + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS pms_plat_it_checklist ("
                + "id bigint not null auto_increment primary key, title varchar(128), done bit(1), "
                + "order_id bigint, due_date date, " + auditColumns + ") "
                + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    static class RecordingEvents implements BusinessEventPort {
        final List<BusinessEventRecord> records = new ArrayList<>();

        @Override
        public void append(BusinessEventRecord event) {
            records.add(event);
        }
    }

    static class RecordingAudit implements OperationAuditApi {
        record Entry(Long tenantId, Long actorId, String correlationId, String operationCode,
                     String aggregateType, String aggregateKey, String resultCode) {
        }

        final List<Entry> records = new ArrayList<>();

        @Override
        public void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                           Long requestId, String resultCode, Map<String, ?> safeDetail) {
        }

        @Override
        public void record(Long tenantId, Long actorId, String correlationId, String operationCode,
                           String aggregateType, String aggregateKey, String resultCode,
                           Map<String, ?> safeDetail) {
            records.add(new Entry(tenantId, actorId, correlationId, operationCode,
                    aggregateType, aggregateKey, resultCode));
        }
    }

    static class InMemoryExecutionStore implements OperationExecutionStore {
        record Entry(String digest, String status, BusinessOperationReceipt receipt) {
        }

        final Map<OperationExecutionKey, Entry> store = new ConcurrentHashMap<>();

        @Override
        public boolean reserve(OperationExecutionKey key, String requestDigest) {
            return store.putIfAbsent(key, new Entry(requestDigest, "IN_PROGRESS", null)) == null;
        }

        @Override
        public Optional<StoredExecution> findExisting(OperationExecutionKey key) {
            return Optional.ofNullable(store.get(key))
                    .map(entry -> new StoredExecution(entry.digest(), entry.status(), entry.receipt()));
        }

        @Override
        public void complete(OperationExecutionKey key, String aggregateType, String resourceKey,
                             BusinessOperationReceipt receipt) {
            Entry entry = store.get(key);
            if (entry == null) {
                throw new IllegalStateException("预约缺失: " + key);
            }
            store.put(key, new Entry(entry.digest(), "COMPLETED", receipt));
        }
    }

    static class OperationCatalog implements BusinessModelCatalog {

        private final Map<String, BusinessModelDescriptor> descriptors = Map.of(
                "it/order", new BusinessModelDescriptor("it", "order", "IT_ORDER", 1,
                        BusinessModelKind.AGGREGATE_ROOT, "订单", null,
                        List.of(field("orderNo", EntityField.Type.TEXT), field("amount", EntityField.Type.NUMBER),
                                field("status", EntityField.Type.TEXT)),
                        List.of(new BusinessRelationDescriptor("checklists", "订单清单", "it", "checklist",
                                BusinessRelationDescriptor.Cardinality.TO_MANY, true, "orderId")),
                        List.of(new BusinessOperationDescriptor("create", 1, "创建",
                                        BusinessOperationDescriptor.StandardOperationKind.CREATE),
                                new BusinessOperationDescriptor("save", 1, "保存",
                                        BusinessOperationDescriptor.StandardOperationKind.UPDATE)),
                        List.of(), "it_order"),
                "it/checklist", new BusinessModelDescriptor("it", "checklist", "IT_CHECKLIST", 1,
                        BusinessModelKind.DETAIL, "清单", null,
                        List.of(field("title", EntityField.Type.TEXT), field("done", EntityField.Type.BOOLEAN),
                                field("orderId", EntityField.Type.NUMBER), field("dueDate", EntityField.Type.DATE)),
                        List.of(), List.of(), List.of(), null));

        private static BusinessFieldDescriptor field(String code, EntityField.Type type) {
            return new BusinessFieldDescriptor(code, code, type, false, true, true, null);
        }

        @Override
        public Optional<BusinessModelDescriptor> find(String ownerModule, String entityType) {
            return Optional.ofNullable(descriptors.get(ownerModule + "/" + entityType));
        }

        @Override
        public Optional<BusinessModelDescriptor> findByStableCode(String stableCode) {
            return descriptors.values().stream()
                    .filter(descriptor -> descriptor.stableCode().equals(stableCode)).findFirst();
        }

        @Override
        public List<BusinessModelDescriptor> all() {
            return List.copyOf(descriptors.values());
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @MapperScan("cn.iocoder.yudao.module.pms.platform.support.it")
    @Import({YudaoDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, DruidDataSourceAutoConfigure.class,
            YudaoMybatisAutoConfiguration.class, MybatisPlusAutoConfiguration.class,
            MybatisPlusJoinAutoConfiguration.class, SpringUtil.class,
            BusinessEntityPersistenceRegistry.class})
    static class TestApplication {

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        TransactionOperations transactionOperations(DataSource dataSource) {
            return new TransactionTemplate(
                    new org.springframework.jdbc.support.JdbcTransactionManager(dataSource));
        }

        @Bean
        BusinessModelContributor itContributor(ItOrderMapper orderMapper, ItChecklistMapper checklistMapper) {
            OperationCatalog catalog = new OperationCatalog();
            return () -> List.of(
                    new BusinessModelDeclaration(catalog.find("it", "order").orElseThrow(),
                            ItOrderEntity.class, (BaseMapper<ItOrderEntity>) orderMapper, null),
                    new BusinessModelDeclaration(catalog.find("it", "checklist").orElseThrow(),
                            ItChecklistEntity.class, (BaseMapper<ItChecklistEntity>) checklistMapper, null));
        }

        @Bean
        BusinessModelCatalog operationCatalog() {
            return new OperationCatalog();
        }

        @Bean
        BusinessCallerContext testCallerContext() {
            return () -> new AbstractBusinessApplicationService.ResolvedCaller(1L, 42L, "it-default-svc");
        }

        @Bean
        RecordingEvents recordingEvents() {
            return new RecordingEvents();
        }

        @Bean
        RecordingAudit recordingAudit() {
            return new RecordingAudit();
        }

        @Bean
        InMemoryExecutionStore inMemoryExecutionStore() {
            return new InMemoryExecutionStore();
        }

        @Bean
        DefaultBusinessApplicationService defaultBusinessApplicationService(
                BusinessCallerContext callerContext, BusinessModelCatalog catalog,
                BusinessEntityPersistenceRegistry persistence, BusinessModelAccessStubs stubs,
                InMemoryExecutionStore executionStore, RecordingEvents events, RecordingAudit audit,
                TransactionOperations transactionOperations) {
            return new DefaultBusinessApplicationService(callerContext, catalog, persistence,
                    stubs.guard(), executionStore, events, audit, transactionOperations);
        }

        @Bean
        BusinessOperationDispatcher businessOperationDispatcher(
                BusinessEntityPersistenceRegistry persistence,
                DefaultBusinessApplicationService defaultService) {
            return new BusinessOperationDispatcher(persistence, defaultService);
        }

        @Bean
        BusinessModelAccessStubs businessModelAccessStubs() {
            return new BusinessModelAccessStubs();
        }
    }

    static class BusinessModelAccessStubs {
        final BusinessAccessGuard guard = new BusinessAccessGuard() {
            @Override
            public void requireReadable(BusinessModelDescriptor descriptor, cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor actor, String sceneCode) {
            }

            @Override
            public void requireWritable(BusinessModelDescriptor descriptor, cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor actor, String sceneCode) {
            }
        };

        BusinessAccessGuard guard() {
            return guard;
        }
    }
}
