package cn.iocoder.yudao.module.pms.commerce.service.authority;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApi;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestApiImpl;
import cn.iocoder.yudao.module.pms.commerce.api.authority.CommerceAuthorityIngestException;
import cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityBatchCommand;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.ErpOrderSyncMapper;
import cn.iocoder.yudao.module.pms.commerce.service.sync.DppmsOrderSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic rows only; every test rolls back. Never connects to the DPPMS source. */
@EnabledIfSystemProperty(named="skipITs", matches="false")
@SpringBootTest(classes={CommerceAuthorityIngestMySqlTest.TestApplication.class,
        DppmsOrderSyncAdapterMySqlTest.AdapterConfiguration.class}, webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Transactional
class DppmsOrderSyncAdapterMySqlTest {
    @Resource DppmsOrderSyncAdapter adapter;
    @Resource CommerceAuthorityIngestApi authority;
    @Resource JdbcTemplate jdbc;
    @Resource org.apache.ibatis.session.SqlSession sqlSession;
    private final long tenant = 8_000_000_000L + new Random().nextInt(1_000_000_000);
    private final String number = "ADAPTER-TEST-" + UUID.randomUUID();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        assertEquals("npdms_test", System.getenv("NPDMS_DB_NAME"), "Only the approved local test DB is allowed");
        assertEquals("23316", System.getenv("NPDMS_MYSQL_PORT"));
        CommerceAuthorityIngestMySqlTest.mysqlProperties(registry);
    }
    @BeforeEach void setup() { TenantContextHolder.setTenantId(tenant); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @org.springframework.test.context.transaction.AfterTransaction
    void verifyRollbackLeavesNoBusinessRows() {
        assertEquals(0,count("com_sales_order_line"));
        assertEquals(0,count("com_sales_order"));
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void applyRequiresAnOuterTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> adapter.apply(batch(row("ORDER","1"))));
        assertEquals(0,count("com_sales_order"));
    }

    @Test void signedReturnUnknownUnitPersistsReplaysAndLaterConfirmsUnit() {
        var input = batch(row("LINE","3"),row("ORDER","1"),row("ORDER","2"));
        assertTrue(adapter.preview(input).stream().allMatch(c -> "CREATED".equals(c.action())));
        assertEquals(0, count("com_sales_order"));
        var created = adapter.apply(input);
        assertEquals(created.get(0).targetId(),created.get(1).targetId());
        assertEquals(1,count("com_sales_order"));
        assertEquals(1,count("com_sales_order_line"));
        var stored = line();
        assertEquals(0,new BigDecimal("-4").compareTo((BigDecimal)stored.get("order_qty")));
        assertEquals(0,new BigDecimal("-3").compareTo((BigDecimal)stored.get("delivered_qty")));
        assertNull(stored.get("unit_code"));
        assertEquals("PENDING_AUTHORITY",stored.get("quantity_status"));
        assertEquals("RETURNED",stored.get("source_lifecycle_status"));
        assertTrue(adapter.apply(input).stream().allMatch(c -> "UNCHANGED".equals(c.action())));
        var confirmed = edit(row("LINE","3"),"unitCode","PCS","sourceUpdatedAt","2026-09-14T11:00:00");
        assertEquals("UPDATED",adapter.apply(batch(confirmed)).getFirst().action());
        assertEquals("CONFIRMED",line().get("quantity_status"));
        assertEquals("PCS",line().get("unit_code"));
        assertEquals(1,count("com_sales_order_line"));
    }

    @Test void staleAndSameVersionChangesCannotOverwriteAcceptedFacts() {
        adapter.apply(batch(row("ORDER","1"),row("LINE","2")));
        var stale=edit(row("LINE","2"),"sourceUpdatedAt","2026-09-13T10:00:00","itemCode","STALE");
        var changed=edit(row("LINE","3"),"itemCode","CONFLICT");
        var result=adapter.apply(batch(stale,changed));
        assertTrue(result.stream().allMatch(c -> "ISSUE".equals(c.action())));
        assertTrue(result.getFirst().message().contains("STALE_SOURCE_VERSION"));
        assertTrue(result.getLast().message().contains("SOURCE_VERSION_PAYLOAD_CONFLICT"));
        assertEquals("ITEM-1",line().get("item_code"));
    }

    @Test void issuesAreIsolatedAndTenantCannotSeeAnotherTenantsParent() {
        adapter.apply(batch(row("ORDER","1")));
        TenantContextHolder.setTenantId(tenant+1);
        assertTrue(adapter.preview(batch(row("LINE","2"))).getFirst().message().contains("PARENT_ORDER_MISSING"));
        TenantContextHolder.setTenantId(tenant);
        var invalid=edit(row("LINE","2"),"migrationIssue","DUPLICATE_BUSINESS_KEY_CONFLICT");
        var valid=edit(row("LINE","3"),"lineNo","20");
        var result=adapter.apply(batch(invalid,valid));
        assertEquals(List.of("ISSUE","CREATED"),result.stream().map(Change::action).toList());
        assertEquals(1,count("com_sales_order_line"));
        var error=assertThrows(CommerceAuthorityIngestException.class,()->authority.ingestBatch(
                new CommerceAuthorityBatchCommand(tenant+1,"test","test","ERP","test",List.of(),List.of(
                        new cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSalesOrderFact(
                                "test",null,"V1","01",number,"1",null,null,null,null,
                                cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceSourceLifecycleStatus.RETURNED,LocalDateTime.now())),
                        List.of(),List.of(),LocalDateTime.now(),"test")));
        assertEquals(CommerceAuthorityIngestException.Code.TENANT_CONTEXT_MISMATCH,error.getCode());
    }

    @Test void deletedIdentityAndConflictingRowsNeverRecreateOrPartiallyWrite() {
        var conflict=edit(row("ORDER","2"),"customerName","DIFFERENT");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row("ORDER","1"),conflict)));
        assertEquals(0,count("com_sales_order"));
        adapter.apply(batch(row("ORDER","1"),row("LINE","3")));
        jdbc.update("UPDATE com_sales_order_line SET deleted=1 WHERE tenant_id=?",tenant);
        sqlSession.clearCache();
        assertEquals("CONFLICT",adapter.apply(batch(row("LINE","3"))).getFirst().action());
        jdbc.update("UPDATE com_sales_order SET deleted=1 WHERE tenant_id=?",tenant);
        sqlSession.clearCache();
        assertEquals("CONFLICT",adapter.apply(batch(row("ORDER","1"))).getFirst().action());
        assertEquals(1,count("com_sales_order"));
        assertEquals(1,count("com_sales_order_line"));
    }

    @Test void normalSalesRejectNegativeButAcceptPositiveQuantities() {
        var head=edit(row("ORDER","1"),"orderType","0");
        var negative=edit(row("LINE","2"),"orderType","0");
        assertEquals("ISSUE",adapter.apply(batch(head,negative)).getLast().action());
        assertEquals(0,count("com_sales_order_line"));
        var positive=edit(negative,"orderQuantity","4","openQuantity","1","unitCode","PCS");
        assertEquals("CREATED",adapter.apply(batch(positive)).getFirst().action());
        assertEquals(0,new BigDecimal("3").compareTo((BigDecimal)line().get("delivered_qty")));
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE tenant_id=?",Integer.class,tenant); }
    private Map<String,Object> line() { return jdbc.queryForMap("SELECT * FROM com_sales_order_line WHERE tenant_id=?",tenant); }
    private Batch batch(Row... rows) { return new Batch("adapter-functional-test",List.of(rows),List.of(),true,"RETAIN",false,"UPSERT",false); }
    private Row row(String object,String id) {
        var fields=new LinkedHashMap<String,Object>(Map.of("erpSource","D365","companyCode","01","orderType","1",
                "orderNo",number,"sourceUpdatedAt","2026-09-14T10:00:00","customerName","TEST"));
        if("LINE".equals(object)) fields.putAll(Map.of("lineNo","10","orderQuantity","-4","openQuantity","-1","itemCode","ITEM-1"));
        return new Row(object,id,fields,null);
    }
    private Row edit(Row row,Object... pairs) {
        var fields=new LinkedHashMap<>(row.fields());
        for(int i=0;i<pairs.length;i+=2) fields.put((String)pairs[i],pairs[i+1]);
        return new Row(row.object(),row.sourceKey(),fields,null);
    }
    @Configuration
    @MapperScan(basePackageClasses=ErpOrderSyncMapper.class)
    @Import({DppmsOrderSyncAdapter.class,CommerceAuthorityIngestApiImpl.class})
    static class AdapterConfiguration {}
}
