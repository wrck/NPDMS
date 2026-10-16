package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinitionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CrmProductFieldsTest {
    @Test void completeProductPayloadPreservesPrecisionDatesAndSourceOwnership() {
        var fixture=new GenericSyncEngineTest();
        fixture.setup();
        try {
            var d=fixture.templates.stream().filter(t->t.key().equals("crm-product")).findFirst().orElseThrow().definition();
            var validator=new SyncDefinitionValidator(List.of());
            ReflectionTestUtils.setField(validator,"genericEngine",fixture.engine);
            validator.validate(d);
            assertNotNull(ReflectionTestUtils.invokeMethod(
                    cn.iocoder.yudao.module.pms.integration.sync.MysqlSyncReader.class,"compile",d.sources().getFirst()));
            fixture.jdbc.execute("ALTER TABLE ast_product_official_info ALTER COLUMN list_price DECIMAL(23,10)");
            for(String col:List.of("end_of_sale_date","end_of_support_date","end_of_life_date","last_renewal_date"))
                fixture.jdbc.execute("ALTER TABLE ast_product_official_info ALTER COLUMN "+col+" TIMESTAMP(3)");
            var values=new LinkedHashMap<String,Object>();
            values.put("itemCode","ITEM-CRM");values.put("productCode","FAMILY-CRM");values.put("productSubCode","SUB-CRM");
            values.put("itemModel","MODEL-CRM");values.put("itemDesc","产品描述");values.put("remark","完整规格");
            values.put("listPrice",new BigDecimal("1234567890123.1234567890"));values.put("status",2);
            values.put("BU","BU-CRM");values.put("productLine","LINE-CRM");values.put("orgId",1);
            values.put("createTime","2026-01-01T10:00:00.123");values.put("updateTime","2026-10-15T10:00:00.456");
            values.put("statecode",0);values.put("statuscode",1);values.put("productStage",3);
            for(String key:List.of("endOfSaleDate","endOfSupportDate","endOfLifeDate","lastRenewalDate"))values.put(key,"2027-10-15T12:34:56.789");
            var row=fixture.row(d,"ITEM-CRM",values);
            assertEquals("CREATED",fixture.apply(d,row).getFirst().action());
            var saved=fixture.jdbc.queryForMap("SELECT * FROM ast_product_official_info");
            assertEquals("ITEM-CRM",saved.get("product_code"));assertEquals("FAMILY-CRM",saved.get("product_category"));
            assertEquals("SUB-CRM",saved.get("product_type"));
            assertEquals(values.get("listPrice"),saved.get("list_price"));
            assertEquals("2027-10-15 12:34:56.789",saved.get("end_of_sale_date").toString());
            assertEquals("ACTIVE",saved.get("status"));assertEquals("2",saved.get("crm_status").toString());
            assertEquals("UNCHANGED",fixture.apply(d,row).getFirst().action());
            values.put("listPrice",BigDecimal.ONE);values.put("updateTime","2026-10-14T10:00:00");
            assertEquals("UNCHANGED",fixture.apply(d,fixture.row(d,"ITEM-CRM",values)).getFirst().action());
            assertEquals(saved.get("list_price"),fixture.jdbc.queryForObject("SELECT list_price FROM ast_product_official_info",BigDecimal.class));
            fixture.jdbc.update("UPDATE ast_product_official_info SET source_system='DPPMS'");
            var protectedRow=new DataSyncAdapter.Row(row.object(),row.sourceKey(),row.fields(),null);
            assertEquals("CONFLICT",fixture.apply(d,protectedRow).getFirst().action());
            assertEquals(1,fixture.count("ast_product_official_info"));
            assertEquals("DPPMS",fixture.jdbc.queryForObject("SELECT source_system FROM ast_product_official_info",String.class));
        } finally {TenantContextHolder.clear();}
    }
}
