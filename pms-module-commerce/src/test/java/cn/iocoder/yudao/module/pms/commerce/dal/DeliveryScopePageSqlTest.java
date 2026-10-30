package cn.iocoder.yudao.module.pms.commerce.dal;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.DeliveryScopeMapper;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopePageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryScopePageSqlTest {
    private SqlSession session;
    private DeliveryScopeMapper mapper;

    @BeforeEach void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:scope_product_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = source.getConnection(); var sql = c.createStatement()) {
            sql.execute("CREATE TABLE com_delivery_scope(id BIGINT PRIMARY KEY,tenant_id BIGINT,project_id BIGINT,order_line_id BIGINT,product_code VARCHAR(100),product_desc VARCHAR(100),allocated_qty DECIMAL(20,4),scope_status VARCHAR(20),effective_from TIMESTAMP,effective_to TIMESTAMP,allocation_version BIGINT,deleted BOOLEAN)");
            sql.execute("CREATE TABLE ast_product_official_info(tenant_id BIGINT,product_code VARCHAR(100),product_model VARCHAR(100),product_name VARCHAR(100),status VARCHAR(20),source_system VARCHAR(20),deleted BOOLEAN,UNIQUE(tenant_id,product_code))");
            sql.execute("INSERT INTO com_delivery_scope VALUES(101,1,84499,11,'P1','订单产品',6,'ACTIVE','2026-01-01',NULL,1,FALSE),(102,1,84499,12,'P1','订单产品',6,'ACTIVE','2026-01-01',NULL,1,FALSE),(103,1,84499,13,'P2','订单产品二',12,'ACTIVE','2026-01-01',NULL,1,FALSE),(104,1,84499,14,'MISSING',NULL,1,'ACTIVE','2026-01-01',NULL,1,FALSE),(105,2,84499,15,'P1',NULL,1,'ACTIVE','2026-01-01',NULL,1,FALSE),(106,1,90000,16,'P1',NULL,1,'ACTIVE','2026-01-01',NULL,1,FALSE),(107,1,84499,17,'P1',NULL,1,'ACTIVE','2026-01-01',NULL,1,TRUE),(108,1,84499,18,'P1',NULL,1,'ACTIVE','2026-01-01','2026-02-01',1,FALSE)");
            sql.execute("INSERT INTO ast_product_official_info VALUES(1,'P1','MASTER','主档产品','INACTIVE','OTHER',FALSE),(2,'P2','FOREIGN','其他租户','ACTIVE','CRM',FALSE),(1,'P2','DELETED','已删除','ACTIVE','CRM',TRUE),(1,'P1-SIMILAR','WRONG','相似编码','ACTIVE','CRM',FALSE)");
        }
        var config = new Configuration(new Environment("scope-product", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        try (var input = getClass().getResourceAsStream("/mapper/scope/DeliveryScopeMapper.xml")) {
            var xml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("b'0'", "FALSE");
            new XMLMapperBuilder(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    config, "scope-product", config.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        mapper = session.getMapper(DeliveryScopeMapper.class);
    }

    @AfterEach void close() { if (session != null) session.close(); }
    private DeliveryScopePageQuery query(int offset) {
        return new DeliveryScopePageQuery(1L, List.of(84499L), 84499L, null, false, offset, 2);
    }

    @Test void sourceJoinKeepsOneScopePerRowAcrossPagesAndDoesNotInventMissingModels() {
        assertEquals(4L, mapper.selectCountByProjectScope(query(0)));
        var first = mapper.selectPageByProjectScope(query(0));
        assertEquals(List.of(101L, 102L), first.stream().map(row -> row.getId()).toList());
        assertEquals(List.of("MASTER", "MASTER"), first.stream().map(row -> row.getProductModel()).toList());
        assertEquals("订单产品", first.getFirst().getProductDesc());
        assertEquals("主档产品", first.getFirst().getProductName());
        var second = mapper.selectPageByProjectScope(query(2));
        assertEquals(List.of(103L, 104L), second.stream().map(row -> row.getId()).toList());
        assertTrue(second.stream().allMatch(row -> row.getProductModel() == null));
        assertTrue(mapper.selectPageByProjectScope(query(4)).isEmpty());
    }

    @Test void tenantDeletionEmptyPermissionsAndHistoryFiltersStayEffective() {
        var foreign = new DeliveryScopePageQuery(2L, List.of(84499L), 84499L, null, false, 0, 20);
        var row = mapper.selectPageByProjectScope(foreign).getFirst();
        assertEquals(105L, row.getId());
        assertNull(row.getProductModel(), "其他租户不能读取本租户主档");
        for (var ids : java.util.Arrays.asList(List.<Long>of(), null)) {
            var empty = new DeliveryScopePageQuery(1L, ids, null, null, true, 0, 20);
            assertEquals(0L, mapper.selectCountByProjectScope(empty));
            assertTrue(mapper.selectPageByProjectScope(empty).isEmpty());
        }
        var history = new DeliveryScopePageQuery(1L, List.of(84499L), 84499L, null, true, 0, 20);
        assertEquals(5L, mapper.selectCountByProjectScope(history));
        assertEquals(5, mapper.selectPageByProjectScope(history).size());
    }
}
