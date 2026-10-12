package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DppmsOrderSyncTemplateTest {
    @Test void allCompaniesUseTheApprovedRuleForHeadersAndLines() {
        var definition=DppmsOrderSyncTemplate.create(12L);
        for(var source:definition.sources()) {
            var company=source.mappings().stream().filter(m->"companyCode".equals(m.target())).findFirst().orElseThrow();
            var d=definition.toBuilder().sources(java.util.List.of(source.toBuilder().mappings(java.util.List.of(company)).updatedAt(null).build())).build();
            for(var code:java.util.List.of("01","02","03","04")) {
                var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),java.util.List.of(new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),java.util.List.of(java.util.Map.of("id",1,"compCode",code)))),1);
                var row=new SyncFieldMapper().transform(d,snapshot,java.util.List.of()).getFirst();
                assertEquals("0"+code,row.fields().get("companyCode"));
            }
            var unknown=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),java.util.List.of(new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),java.util.List.of(java.util.Map.of("id",1,"compCode","05")))),1);
            assertThrows(IllegalArgumentException.class,()->new SyncFieldMapper().transform(d,unknown,java.util.List.of()));
        }
    }

    @Test void forwardMappingsKeepProductAndCustomAttributesAtTheAcceptedBusinessFields() {
        var definition=DppmsOrderSyncTemplate.create(12L);
        for(var source:definition.sources()) {
            var selected=source.mappings().stream().filter(m->java.util.Set.of("customInfo","productCode","productDesc").contains(m.target())).toList();
            var d=definition.toBuilder().sources(java.util.List.of(source.toBuilder().mappings(selected).updatedAt(null).build())).build();
            var original=java.util.Map.<String,Object>of("id",7,"customInfo","Original custom value","itemCode","PRODUCT-A","itemDesc","Original product description");
            var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),java.util.List.of(new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),java.util.List.of(original))),1);
            var row=new SyncFieldMapper().transform(d,snapshot,java.util.List.of()).getFirst();
            assertEquals("Original custom value",row.fields().get("customInfo"));
            if("LINE".equals(source.object())) {
                assertEquals("PRODUCT-A",row.fields().get("productCode"));
                assertEquals("Original product description",row.fields().get("productDesc"));
                assertFalse(row.fields().containsKey("itemCode"));assertFalse(row.fields().containsKey("itemDescription"));
            }
            assertEquals("PRODUCT-A",original.get("itemCode"));
        }
    }
    @Test void validatorRejectsLegacyProductAliasesAndAcceptsTheForwardMappings() {
        var adapter=org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.class);
        java.util.List<String> common=java.util.List.of("erpSource","companyCode","orderNo","orderType","sourceUpdatedAt","migrationIssue","customInfo");
        var header=new java.util.ArrayList<>(common);header.addAll(java.util.List.of("customerCode","customerName","salesType","sourceProjectName","orderComment","orderCreateTime","customerRequiredTime","contractNo","executionNo"));
        var line=new java.util.ArrayList<>(common);line.addAll(java.util.List.of("migrationParentExists","lineNo","productCode","productDesc","orderQuantity","openQuantity","bundleCode","warrantyMonth","profitCenter","realExecutionNo","unitCode"));
        var headFields=header.stream().map(n->new cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Field(n,n,"STRING",false)).toList();
        var lineFields=line.stream().map(n->new cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Field(n,n,"STRING",false)).toList();
        var objects=java.util.List.of(new cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.ObjectDescriptor("ORDER","ORDER",headFields,"COM","SalesOrder","com_sales_order",false),new cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.ObjectDescriptor("LINE","LINE",lineFields,"COM","OrderLine","com_sales_order_line",false));
        org.mockito.Mockito.when(adapter.descriptor()).thenReturn(new cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.Descriptor("DPPMS_ERP_ORDER","Orders",objects,java.util.List.of("RETAIN"),java.util.List.of("UPSERT"),false,true));
        org.mockito.Mockito.when(adapter.supportsStreaming()).thenReturn(true);
        var validator=new SyncDefinitionValidator(java.util.List.of(adapter));var current=DppmsOrderSyncTemplate.create(12L);
        assertDoesNotThrow(()->validator.validate(current));
        for(String legacy:java.util.List.of("itemCode","itemDescription")) {
            var source=current.sources().getLast();var mappings=new java.util.ArrayList<>(source.mappings());
            var index=legacy.equals("itemCode")?mappings.stream().map(SyncDefinition.Mapping::target).toList().indexOf("productCode"):mappings.stream().map(SyncDefinition.Mapping::target).toList().indexOf("productDesc");
            mappings.set(index,mappings.get(index).toBuilder().target(legacy).build());
            var broken=current.toBuilder().sources(java.util.List.of(current.sources().getFirst(),source.toBuilder().mappings(mappings).build())).build();
            assertThrows(IllegalArgumentException.class,()->validator.validate(broken));
        }
    }
    @Test void readsPreviousJsonTimestampBindingsAndCurrentJdbcValuesIdentically() {
        var time=java.time.LocalDateTime.of(2026,8,2,23,51,24);
        var fields=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseMap(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(java.util.Map.of("time",time)));
        assertEquals(time,SyncFieldMapper.sourceTime(fields.get("time")));
        assertEquals(time,SyncFieldMapper.sourceTime(time));
        assertEquals(time,SyncFieldMapper.sourceTime(time.toString()));
    }
    @Test void compilesReadOnlyStreamingSourcesAndRetainsRawIdsAndVersions() {
        var definition=DppmsOrderSyncTemplate.create(12L);
        for(var source:definition.sources()) {
            var sql=MysqlSyncReader.compile(source);
            assertTrue(sql.sql().contains("r.*"));
            assertTrue(sql.sql().contains("payloadVariants"));
            assertEquals("id",source.sourceKey());
            assertFalse(source.syncPrimaryKey());
            assertEquals(0,sql.values().size());
            assertFalse(sql.sql().contains(":afterId"));
        }
        assertFalse(definition.autoPaging());
        assertEquals("STREAMING_CURSOR",definition.effectiveReadStrategy());
        assertEquals(2000,definition.effectiveFetchSize());
        assertEquals(1000,definition.effectiveChunkSize());
        assertEquals("RESTART_ALL",definition.effectiveRestartPolicy());
        assertEquals("RETAIN",definition.missingPolicy());
        assertEquals("ONCE",definition.mode());
    }
}
