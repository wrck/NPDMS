package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DppmsOrderSyncTemplateTest {
    @Test void ordersAndLinesReadIndependentlyWithApprovedBusinessKeys() {
        var definition=DppmsOrderSyncTemplate.create(12L);
        assertEquals("TABLE_MAPPING",definition.adapter());
        assertEquals(12L,definition.connectionId());
        assertEquals("ONCE",definition.mode());
        assertEquals("STREAMING_CURSOR",definition.effectiveReadStrategy());
        assertEquals("RESTART_ALL",definition.effectiveRestartPolicy());
        assertFalse(definition.autoPaging());
        assertEquals(List.of("ORDER","LINE"),definition.sources().stream().map(SyncDefinition.Source::object).toList());
        var order=definition.sources().getFirst();var line=definition.sources().getLast();
        assertNotNull(order.sql());assertNull(order.queryRef());
        assertNull(order.groupKey());assertNull(order.rowProjection());
        assertNotNull(line.sql());assertNull(line.queryRef());
        assertEquals(List.of("source_system","company_code","order_type","order_no"),order.targets().getFirst().keys());
        assertEquals(List.of("source_system","company_code","order_type","order_no","line_no"),line.targets().getFirst().keys());
        for(var source:definition.sources()) {
            assertEquals("migrationSourceKey",source.sourceKey());assertFalse(source.syncPrimaryKey());
            assertEquals("migrationIssue",source.issueField());
            assertEquals("source_updated_at",source.targets().getFirst().newerBy());
            assertNotNull(source.targets().getFirst().sameVersionColumns());
            assertDoesNotThrow(()->MysqlSyncReader.compile(source));
            assertFalse(source.sql().contains("pm_project"));
            assertTrue(source.sql().contains("DUPLICATE_BUSINESS_KEY_CONFLICT"));
        }
        var parentLookup=line.targets().getFirst().lookups().getFirst();
        assertEquals("com_sales_order",parentLookup.table());
        assertEquals(Set.of("source_system","company_code","order_type","order_no"),parentLookup.match().keySet());
        assertEquals("RETAIN",parentLookup.onMissing());
    }

    @Test void groupedProjectionPreservesOriginalRowsWithoutMakingRawIdsTheSourceIdentity() {
        var current=DppmsOrderSyncTemplate.create(12L);
        // A frozen historical run still decodes its original grouped shape.
        var root=current.sources().getFirst().toBuilder().groupKey("_groupKey").rowProjection("_sourceRows").build();
        var definition=current.toBuilder().sources(List.of(root,
                current.sources().getLast().toBuilder().sql(null).queryRef("ORDER").build())).build();
        var raw=new LinkedHashMap<String,Object>();
        for(String field:List.of("id","orderNumber","lineNum","itemCode","itemDesc","orderQuantity","openQuantity","bundleCode","warrantyMonth","lineType","compCode","profitCenter","realOrderExecNumber","source","customInfo","syncTime"))raw.put(field,"original");
        raw.put("id",7);raw.put("orderQuantity",185);raw.put("openQuantity",12);raw.put("customInfo",null);
        var orderFields=new LinkedHashMap<String,Object>();orderFields.put("migrationSourceKey","stable-order-business-hash");
        // MySQL JSON_OBJECT retains SQL NULL; the application's NON_NULL serializer does not.
        var rawJson=JsonUtils.toJsonString(raw);rawJson=rawJson.substring(0,rawJson.length()-1)+",\"customInfo\":null}";
        var payload="["+JsonUtils.toJsonString(Map.of("object","ORDER","fields",orderFields))
                +",{\"object\":\"LINE\",\"fields\":{\"migrationSourceKey\":\"stable-line-business-hash\",\"originalRows\":["
                +rawJson+"],\"originalRowCount\":1}}]";
        var projected=SyncSourceProjection.expand(new SyncSourceProjection.Input(definition,root,List.of(Map.of("_groupKey","stable-order-business-hash","_sourceRows",payload))));
        var line=projected.stream().filter(source->"LINE".equals(source.object())).findFirst().orElseThrow().rows().getFirst();
        assertEquals("stable-line-business-hash",line.get("migrationSourceKey"));
        var originals=JsonUtils.parseArray((String)line.get("originalRows"),Map.class);var original=originals.getFirst();
        assertEquals(raw,original);assertEquals(16,original.size());
        assertEquals(185,original.get("orderQuantity"));assertEquals(12,original.get("openQuantity"));
        assertNotEquals(original.get("id").toString(),line.get("migrationSourceKey"));
    }

    @Test void readsPreviousJsonTimestampBindingsAndCurrentJdbcValuesIdentically() {
        var time=java.time.LocalDateTime.of(2026,8,2,23,51,24);
        var fields=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseMap(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(java.util.Map.of("time",time)));
        assertEquals(time,SyncFieldMapper.sourceTime(fields.get("time")));
        assertEquals(time,SyncFieldMapper.sourceTime(time));
        assertEquals(time,SyncFieldMapper.sourceTime(time.toString()));
    }
}
