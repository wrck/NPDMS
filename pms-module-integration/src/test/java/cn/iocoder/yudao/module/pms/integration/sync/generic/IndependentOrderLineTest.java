package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.module.pms.integration.sync.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndependentOrderLineTest {
    @Test void unchangedSourceCanNormalizeOnlyTheSameOriginalCustomerCode() {
        var f=new GenericSyncEngineTest();f.setup();
        try {
            var all=DppmsOrderSyncTemplate.create(1L);
            var d=all.toBuilder().sources(List.of(all.sources().getFirst())).build();
            var values=new LinkedHashMap<String,Object>();
            values.put("source","SAP");values.put("company_code","001");values.put("orderNumber","ORDER");
            values.put("orderType","0");values.put("sourceRecordKey","DPPMS|SAP|001|0|ORDER");
            values.put("sourceVersion","v1");values.put("migrationVersion","2026-10-15T10:00:00");
            values.put("syncReceivedAt","2026-10-15T11:00:00");values.put("customerCode","CA13");
            values.put("migrationCustomerCode","CA13");
            assertEquals("CREATED",f.apply(d,f.row(d,"order",values)).getFirst().action());
            values.put("migrationCustomerCode","C00A13");
            assertEquals("UPDATED",f.apply(d,f.row(d,"order",values)).getFirst().action());
            assertEquals("C00A13",f.jdbc.queryForObject("SELECT customer_code FROM com_sales_order",String.class));
            values.put("migrationCustomerCode","C00A14");
            assertEquals("CONFLICT",f.apply(d,f.row(d,"order",values)).getFirst().action());
            assertEquals("C00A13",f.jdbc.queryForObject("SELECT customer_code FROM com_sales_order",String.class));
            values.put("migrationVersion","2026-10-14T10:00:00");
            assertEquals("UNCHANGED",f.apply(d,f.row(d,"order",values)).getFirst().action());
        } finally {f.clear();}
    }

    @Test void orphanLinesStayDistinctAndExactLocalParentCanBeAssociatedLater() {
        var f=new GenericSyncEngineTest();f.setup();
        try {
            var all=DppmsOrderSyncTemplate.create(1L);
            var validator=new SyncDefinitionValidator(List.of());
            ReflectionTestUtils.setField(validator,"genericEngine",f.engine);
            assertDoesNotThrow(()->validator.validate(all));
            var d=all.toBuilder().sources(List.of(all.sources().getLast())).build();
            var values=new LinkedHashMap<String,Object>();
            values.put("source","SAP");values.put("company_code","001");values.put("orderNumber","ORPHAN");
            values.put("lineType","0");values.put("lineNum","1");values.put("sourceRecordKey","DPPMS|SAP|001|0|ORPHAN|1");
            values.put("sourceVersion","v1");values.put("migrationVersion","2026-10-15T10:00:00");
            values.put("syncReceivedAt","2026-10-15T11:00:00");
            values.put("orderQuantity",10);values.put("openQuantity",4);values.put("deliveredQuantity",6);
            values.put("itemCode","PRODUCT-1");
            assertEquals("CREATED",f.apply(d,f.row(d,"line-1",values)).getFirst().action());
            assertNull(f.jdbc.queryForObject("SELECT order_id FROM com_sales_order_line",Long.class));
            values.put("lineNum","2");values.put("sourceRecordKey","DPPMS|SAP|001|0|ORPHAN|2");
            assertEquals("CREATED",f.apply(d,f.row(d,"line-2",values)).getFirst().action());
            assertEquals(2,f.count("com_sales_order_line"));
            f.jdbc.update("INSERT INTO com_sales_order(id,tenant_id,source_system,company_code,order_type,order_no) VALUES(71,1,'SAP','001','0','ORPHAN')");
            f.jdbc.update("INSERT INTO ast_product_official_info(id,tenant_id,product_code) VALUES(81,1,'PRODUCT-1')");
            assertEquals("UPDATED",f.apply(d,f.row(d,"line-2",values)).getFirst().action());
            assertEquals(71L,f.jdbc.queryForObject("SELECT order_id FROM com_sales_order_line WHERE line_no='2'",Long.class));
            assertEquals(81L,f.jdbc.queryForObject("SELECT product_id FROM com_sales_order_line WHERE line_no='2'",Long.class));
            assertNull(f.jdbc.queryForObject("SELECT order_id FROM com_sales_order_line WHERE line_no='1'",Long.class));
            values.put("migrationIssue","DUPLICATE_BUSINESS_KEY_CONFLICT");
            assertEquals("ISSUE",f.apply(d,f.row(d,"line-2",values)).getFirst().action());
            assertEquals(2,f.count("com_sales_order_line"));
        } finally { f.clear(); }
    }
}
