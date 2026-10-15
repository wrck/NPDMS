package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentSourceFactsApi;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectShipmentConfigurationTest {
    static SyncDefinition definition() throws Exception {
        try(var in=new ClassPathResource("sync/p15-decided.json").getInputStream()) {
            return JsonUtils.parseObject(in.readAllBytes(),SyncDefinition.class).executionActorId(2L);
        }
    }
    @Test void decidedConfigurationValidatesWithoutLegacyProjectOrOrderQueries() throws Exception {
        var f=new GenericSyncEngineTest();f.setup();
        try {
            var validator=new SyncDefinitionValidator(List.of());ReflectionTestUtils.setField(validator,"genericEngine",f.engine);
            var d=definition();assertDoesNotThrow(()->validator.validate(d));
            assertEquals("LOCAL_OWNER",d.sources().getFirst().readMode());
            for(var s:d.sources().subList(1,5)) {
                assertNull(s.queryRef());assertFalse(s.sql().contains("pm_project"));assertFalse(s.sql().contains("pm_order_data_from_erp"));
            }
        } finally {f.clear();}
    }
    @Test void packageUsesLocalDictionaryAndKeepsUnrecognizedOriginalCode() throws Exception {
        var f=new GenericSyncEngineTest();f.setup();
        try {
            var dictionaries=mock(DictDataApi.class);ReflectionTestUtils.setField(f.engine,"dictionaries",dictionaries);
            var item=new DictDataRespDTO().setDictType("01").setValue("C1").setLabel("原物流名称");
            when(dictionaries.getDictDataList("01")).thenReturn(List.of(item));
            var all=definition();var d=all.toBuilder().sources(List.of(all.sources().get(2))).build();
            var row=f.row(d,"PACK-1",Map.of("packlist_id","PACK-1","emsCompany","C1","con_id","SOURCE-PARENT"));
            assertEquals("CREATED",f.apply(d,row).getFirst().action());
            assertEquals("原物流名称",f.jdbc.queryForObject("SELECT carrier_name FROM com_shipment_package",String.class));
            assertEquals("SOURCE-PARENT",f.jdbc.queryForObject("SELECT shipment_contract_source_key FROM com_shipment_package",String.class));
            assertEquals("CREATED",f.apply(d,f.row(d,"PACK-2",Map.of("packlist_id","PACK-2","emsCompany","UNKNOWN"))).getFirst().action());
            assertEquals("UNKNOWN",f.jdbc.queryForObject("SELECT carrier_name FROM com_shipment_package WHERE package_no='PACK-2'",String.class));
            when(dictionaries.getDictDataList("01")).thenReturn(List.of(item,item));
            assertThrows(IllegalArgumentException.class,()->f.apply(d,row));assertEquals(2,f.count("com_shipment_package"));
        } finally {f.clear();}
    }
    @Test void localContractUpdatesApprovedCustomerFieldsWithoutTakingAnotherAuthority() throws Exception {
        var f=new GenericSyncEngineTest();f.setup();
        try {
            var all=definition();var d=all.toBuilder().sources(List.of(all.sources().getFirst())).build();
            f.jdbc.update("INSERT INTO com_contract(id,tenant_id,company_code,contract_no,master_source_system,master_source_record_key,customer_code) VALUES(1,1,'001','C1','DPPMS_ERP','legacy-source','OLD'),(2,1,'001','C2','MANUAL','manual-source','KEEP')");
            var values=new HashMap<String,Object>();values.put("company_code","001");values.put("contract_no","C1");
            values.put("customer_code","C00001");values.put("customer_name","Local customer");values.put("master_origin","DPPMS_ERP");
            assertEquals("UPDATED",f.apply(d,f.row(d,"local-C1",values)).getFirst().action());
            assertEquals("C00001",f.jdbc.queryForObject("SELECT customer_code FROM com_contract WHERE id=1",String.class));
            assertEquals("legacy-source",f.jdbc.queryForObject("SELECT master_source_record_key FROM com_contract WHERE id=1",String.class));
            values.put("contract_no","C2");assertEquals("CONFLICT",f.apply(d,f.row(d,"local-C2",values)).getFirst().action());
            assertEquals("KEEP",f.jdbc.queryForObject("SELECT customer_code FROM com_contract WHERE id=2",String.class));
        } finally {f.clear();}
    }
    @Test void eventUsesExactLocalPackageAndCannotRewriteImmutableTime() throws Exception {
        var f=new GenericSyncEngineTest();f.setup();
        try {
            var parents=mock(ShipmentSourceFactsApi.class);ReflectionTestUtils.setField(f.engine,"shipmentFacts",parents);
            ReflectionTestUtils.setField(f.engine,"dictionaries",mock(DictDataApi.class));
            var d=definition();var event=d.sources().get(3);
            var eventOnly=d.toBuilder().sources(List.of(event)).build();
            var row=f.row(eventOnly,"EVENT-1",Map.of("barcode","SN-1","pack_id","P1","event_type","SHIPMENT","rma_marked",0));
            // The older generic fixture deliberately retains its legacy FK; supply its parent without changing that fixture.
            f.jdbc.update("INSERT INTO ast_device(id,tenant_id,sn) VALUES(1,1,'SN-1')");
            when(parents.read(any())).thenReturn(List.of());
            assertEquals("ISSUE",f.apply(d,row).getFirst().action());assertEquals(0,f.count("ast_device_shipment"));
            var time=LocalDateTime.of(2020,1,1,12,0);
            when(parents.read(any())).thenReturn(List.of(new ShipmentSourceFactsApi.Fact("P1",time,"CONTRACT-1")));
            assertEquals("CREATED",f.apply(d,row).getFirst().action());
            assertEquals("CONTRACT-1",f.jdbc.queryForObject("SELECT contract_no FROM ast_device_shipment",String.class));
            when(parents.read(any())).thenReturn(List.of(new ShipmentSourceFactsApi.Fact("P1",time.plusDays(1),"CONTRACT-1")));
            assertEquals("CONFLICT",f.apply(d,row).getFirst().action());
            assertEquals(time,f.jdbc.queryForObject("SELECT shipment_time FROM ast_device_shipment",java.sql.Timestamp.class).toLocalDateTime());
            assertEquals(1,f.count("ast_device_shipment"));
        } finally {f.clear();}
    }
}
