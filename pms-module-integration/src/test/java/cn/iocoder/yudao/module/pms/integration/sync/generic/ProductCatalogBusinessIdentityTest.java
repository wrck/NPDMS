package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import org.junit.jupiter.api.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the real engine/DDL fixture; asserts identity and source-authority behavior, not configuration text. */
class ProductCatalogBusinessIdentityTest {
    GenericSyncEngineTest fixture;
    JdbcTemplate jdbc;
    @BeforeEach void setup(){fixture=new GenericSyncEngineTest();fixture.setup();jdbc=fixture.jdbc;}
    @AfterEach void clear(){fixture.clear();}
    DataSyncAdapter.Row row(SyncDefinition d,String key,Map<String,Object> values){return fixture.row(d,key,values);}
    List<DataSyncAdapter.Change> apply(SyncDefinition d,DataSyncAdapter.Row row){return fixture.apply(d,row);}
    int count(String table){return fixture.count(table);}
    SyncDefinition productDefinition() {
        return fixture.templates.stream().filter(t->t.key().equals("crm-product")).findFirst().orElseThrow().definition();
    }
    Map<String,Object> product(String code,String name,String date) {
        return Map.of("itemCode",code,"itemModel","M","itemDesc",name,"statecode",0,"updateTime",date);
    }
    @Test void changedSourceKeyForSameProductUpdatesTheExistingId() {
        var d=productDefinition();var first=apply(d,row(d,"P",product("P","Original","2026-01-01T00:00:00"))).getFirst();
        var next=apply(d,row(d," P ",product(" P ","Later","2026-01-02T00:00:00"))).getFirst();
        assertEquals("UPDATED",next.action());assertEquals(first.targetId(),next.targetId());
        assertEquals(1,count("ast_product_official_info"));
        assertEquals("Later",jdbc.queryForObject("SELECT product_name FROM ast_product_official_info",String.class));
        assertEquals("P",jdbc.queryForObject("SELECT source_key FROM ast_product_official_info",String.class));
    }
    @Test void anotherSourceCannotCreateDuplicateOrTakeOverDppmsProduct() {
        jdbc.update("INSERT INTO ast_product_official_info(id,tenant_id,product_code,source_system,source_key,product_name,status) VALUES(500,1,'P','DPPMS','P','Original','ACTIVE')");
        var d=productDefinition();var incoming=row(d,"P",product("P","CRM","2026-01-02T00:00:00"));
        var result=apply(d,incoming).getFirst();assertEquals("CONFLICT",result.action());
        assertTrue(result.message().contains("目标属于其他来源"));
        assertEquals(1,count("ast_product_official_info"));
        assertEquals("Original",jdbc.queryForObject("SELECT product_name FROM ast_product_official_info",String.class));
        assertEquals(500L,jdbc.queryForObject("SELECT id FROM ast_product_official_info",Long.class));
    }
    @Test void sameCodeInOtherTenantIsSeparateButDeletedSameTenantCannotBeRestored() {
        var d=productDefinition();var incoming=row(d,"P",product("P","Original","2026-01-01T00:00:00"));
        apply(d,incoming);cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(2L);apply(d,incoming);
        assertEquals(2,count("ast_product_official_info"));
        jdbc.update("UPDATE ast_product_official_info SET deleted=TRUE WHERE tenant_id=2");
        assertEquals("CONFLICT",apply(d,incoming).getFirst().action());assertEquals(2,count("ast_product_official_info"));
    }
}
