package cn.iocoder.yudao.module.pms.platform.support.it;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.datasource.config.YudaoDataSourceAutoConfiguration;
import cn.iocoder.yudao.framework.mybatis.config.YudaoMybatisAutoConfiguration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntitySlice;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection.BusinessCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDeclaration;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessRelationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.RevisionRef;
import cn.iocoder.yudao.module.pms.platform.support.access.DefaultBusinessEntityAccess;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
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

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一业务访问真实 MySQL 集成测试：两个不同结构的实体与一个修订实体，
 * 不写任何专用 Provider/Adapter，验证标准读取、受控分页、关系成员与租户隔离。
 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
@SpringBootTest(classes = DefaultBusinessEntityAccessMySqlTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DefaultBusinessEntityAccessMySqlTest {

    @Resource private DefaultBusinessEntityAccess access;
    @Resource private JdbcTemplate jdbcTemplate;

    private long idBase;
    private final EntityActor actor = new EntityActor(1L, 42L, "it-access");

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
        registry.add("spring.datasource.druid.stat-view-servlet.enabled", () -> "false");
        registry.add("yudao.info.base-package", () -> "cn.iocoder.yudao.module.pms.platform.support");
        registry.add("mybatis-plus.global-config.db-config.id-type", () -> "AUTO");
    }

    @BeforeEach
    void setUp() {
        idBase = 771_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L) * 100L;
        createTables();
        insertOrder(idBase + 1, 1L, "SO-001", "11.00", "OPEN", 3L);
        insertOrder(idBase + 2, 1L, "SO-002", "22.00", "OPEN", 1L);
        insertOrder(idBase + 3, 1L, "SO-003", "33.00", "CLOSED", 1L);
        insertOrder(idBase + 4, 1L, "SO-004", "44.00", "OPEN", 1L);
        insertOrder(idBase + 5, 1L, "SO-005", "55.00", "CLOSED", 1L);
        insertOrder(idBase + 6, 2L, "SO-OTHER-TENANT", "99.00", "OPEN", 1L);
        insertChecklist(idBase + 11, 1L, "清单A", false, idBase + 1, LocalDate.of(2026, 10, 1));
        insertChecklist(idBase + 12, 1L, "清单B", true, idBase + 1, LocalDate.of(2026, 10, 2));
        insertChecklist(idBase + 13, 1L, "清单C", false, idBase + 2, LocalDate.of(2026, 10, 3));
        insertOrderRevision(idBase + 21, idBase + 2, 1L, "SO-002-DRAFT", "22.00", "OPEN");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM pms_plat_it_order WHERE id >= ? AND id < ?", idBase, idBase + 100);
        jdbcTemplate.update("DELETE FROM pms_plat_it_checklist WHERE id >= ? AND id < ?", idBase, idBase + 100);
        jdbcTemplate.update("DELETE FROM pms_plat_it_order_revision WHERE id >= ? AND id < ?", idBase, idBase + 100);
    }

    @Test
    void readsCurrentEntitiesOfDifferentStructures() {
        BusinessEntityData order = access.read(EntityDataRef.current(
                new EntityRef(1L, "it", "order", idBase + 1)), actor, "it:detail");

        assertTrue(order.available());
        assertNull(order.revisionId());
        assertEquals(3L, order.concurrencyBasis());
        assertEquals("SO-001", order.fieldValues().get("orderNo"));
        assertEquals(new BigDecimal("11.00"), order.fieldValues().get("amount"));
        assertEquals("OPEN", order.fieldValues().get("status"));
        assertFalse(order.fieldValues().containsKey("title"));

        BusinessEntityData checklist = access.read(EntityDataRef.current(
                new EntityRef(1L, "it", "checklist", idBase + 11)), actor, "it:detail");

        assertTrue(checklist.available());
        assertEquals("清单A", checklist.fieldValues().get("title"));
        assertEquals(Boolean.FALSE, checklist.fieldValues().get("done"));
        assertEquals(idBase + 1, checklist.fieldValues().get("orderId"));
        assertFalse(checklist.fieldValues().containsKey("orderNo"));
    }

    @Test
    void readsSpecifiedRevisionWithRevisionIdentity() {
        BusinessEntityData revision = access.read(
                EntityDataRef.revision(new RevisionRef(
                        new EntityRef(1L, "it", "order", idBase + 2), idBase + 21)), actor, "it:revision");
        assertTrue(revision.available());
        assertEquals(idBase + 21, revision.revisionId());
        assertEquals("SO-002-DRAFT", revision.fieldValues().get("orderNo"));
    }

    @Test
    void reportsUnavailableForMissingAndForeignTenant() {
        BusinessEntityData missing = access.read(EntityDataRef.current(
                new EntityRef(1L, "it", "order", idBase + 99)), actor, "it:detail");
        assertFalse(missing.available());
        assertEquals("ENTITY_NOT_FOUND", missing.unavailableReason());

        BusinessEntityData foreignTenant = access.read(EntityDataRef.current(
                new EntityRef(1L, "it", "order", idBase + 6)), actor, "it:detail");
        assertFalse(foreignTenant.available());
        assertEquals("ENTITY_NOT_FOUND", foreignTenant.unavailableReason());
    }

    @Test
    void pagesWithFilterAndKeysetCursor() {
        BusinessEntityPageQuery firstPage = new BusinessEntityPageQuery("it:list", "it", "order",
                List.of(new BusinessFieldFilter("status", BusinessFieldFilter.Operator.EQ, List.of("OPEN"))),
                2, null);
        BusinessEntitySlice slice1 = access.query(firstPage, actor);

        assertEquals(Completeness.PARTIAL, slice1.completeness());
        assertEquals(2, slice1.members().size());
        assertEquals(List.of("SO-001", "SO-002"), orderNos(slice1));
        assertEquals(slice1.members().getLast().ref().entityId(), Long.parseLong(slice1.nextCursor()));

        BusinessEntitySlice slice2 = access.query(new BusinessEntityPageQuery("it:list", "it", "order",
                List.of(new BusinessFieldFilter("status", BusinessFieldFilter.Operator.EQ, List.of("OPEN"))),
                2, slice1.nextCursor()), actor);
        assertEquals(1, slice2.members().size());
        assertEquals(List.of("SO-004"), orderNos(slice2));
        assertEquals(Completeness.COMPLETE, slice2.completeness());
        assertNull(slice2.nextCursor());
    }

    @Test
    void returnsEmptyForEmptyInFilter() {
        BusinessEntitySlice slice = access.query(new BusinessEntityPageQuery("it:list", "it", "order",
                List.of(new BusinessFieldFilter("orderNo", BusinessFieldFilter.Operator.IN, List.of())),
                10, null), actor);

        assertEquals(Completeness.COMPLETE, slice.completeness());
        assertTrue(slice.members().isEmpty());
    }

    @Test
    void enumeratesRelationMembersByJoinField() {
        BusinessEntitySlice members = access.members(new BusinessCollectionQuery(
                "it", "order", idBase + 1, "checklists", null, 10, null), actor);

        assertEquals(Completeness.COMPLETE, members.completeness());
        assertEquals(2, members.members().size());
        assertEquals(List.of("清单A", "清单B"), members.members().stream()
                .map(member -> member.fieldValues().get("title")).toList());
        assertTrue(members.members().stream().allMatch(member ->
                idBase + 1L == (Long) member.fieldValues().get("orderId")));
    }

    private static List<String> orderNos(BusinessEntitySlice slice) {
        return slice.members().stream().map(member -> (String) member.fieldValues().get("orderNo")).toList();
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
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS pms_plat_it_order_revision ("
                + "id bigint not null auto_increment primary key, entity_id bigint not null, "
                + "order_no varchar(64), amount decimal(18,2), order_status varchar(32), "
                + "revision_no int, source_revision_id bigint, base_effective_revision_id bigint, "
                + "base_entity_version int, revision_state varchar(16), effective bit(1), "
                + "change_reason varchar(255), frozen_by bigint, frozen_at datetime, "
                + auditColumns + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void insertOrder(long id, long tenantId, String orderNo, String amount, String status, long version) {
        jdbcTemplate.update("INSERT INTO pms_plat_it_order (id, tenant_id, order_no, amount, order_status, "
                        + "version, deleted) VALUES (?,?,?,?,?,?,b'0')",
                id, tenantId, orderNo, new BigDecimal(amount), status, version);
    }

    private void insertChecklist(long id, long tenantId, String title, boolean done, Long orderId,
                                 LocalDate dueDate) {
        jdbcTemplate.update("INSERT INTO pms_plat_it_checklist (id, tenant_id, title, done, order_id, due_date, "
                + "version, deleted) VALUES (?,?,?,?,?,?,0,b'0')", id, tenantId, title, done, orderId, dueDate);
    }

    private void insertOrderRevision(long id, Long entityId, long tenantId, String orderNo, String amount,
                                     String status) {
        jdbcTemplate.update("INSERT INTO pms_plat_it_order_revision (id, entity_id, tenant_id, order_no, amount, "
                        + "order_status, revision_no, revision_state, effective, version, deleted) "
                        + "VALUES (?,?,?,?,?,?,1,'FROZEN',b'0',1,b'0')",
                id, entityId, tenantId, orderNo, new BigDecimal(amount), status);
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

    static class AllowAllGuard implements BusinessAccessGuard {
        @Override
        public void requireReadable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode) {
        }

        @Override
        public void requireWritable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode) {
        }
    }

    static class ItCatalog implements BusinessModelCatalog {

        private final Map<String, BusinessModelDescriptor> descriptors = Map.of(
                "it/order", new BusinessModelDescriptor("it", "order", "IT_ORDER", 1,
                        BusinessModelKind.AGGREGATE_ROOT, "订单", null,
                        List.of(field("orderNo", EntityField.Type.TEXT), field("amount", EntityField.Type.NUMBER),
                                field("status", EntityField.Type.TEXT)),
                        List.of(new BusinessRelationDescriptor("checklists", "订单清单", "it", "checklist",
                                BusinessRelationDescriptor.Cardinality.TO_MANY, true, "orderId")),
                        List.of(), List.of(), "it_order"),
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
        BusinessModelContributor itContributor(ItOrderMapper orderMapper, ItChecklistMapper checklistMapper,
                                               ItOrderRevisionMapper revisionMapper) {
            ItCatalog catalog = new ItCatalog();
            return () -> List.of(
                    new BusinessModelDeclaration(catalog.find("it", "order").orElseThrow(),
                            ItOrderEntity.class, (BaseMapper<ItOrderEntity>) orderMapper, revisionMapper),
                    new BusinessModelDeclaration(catalog.find("it", "checklist").orElseThrow(),
                            ItChecklistEntity.class, (BaseMapper<ItChecklistEntity>) checklistMapper, null));
        }

        @Bean
        DefaultBusinessEntityAccess businessEntityAccess(BusinessModelCatalog catalog,
                                                         BusinessEntityPersistenceRegistry persistence) {
            return new DefaultBusinessEntityAccess(catalog, persistence, new AllowAllGuard(), null);
        }

        @Bean
        BusinessModelCatalog itModelCatalog() {
            return new ItCatalog();
        }
    }
}
