package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DppmsExecutionOrderSyncTemplateTest {
    @Test void executionHeadersUseAllApprovedCompanyMappingsAndBlockUnknownCodes() {
        var original=DppmsExecutionOrderSyncTemplate.create(9L);
        var source=original.sources().getFirst();
        var company=source.mappings().stream().filter(m->"companyCode".equals(m.target())).findFirst().orElseThrow();
        var d=original.toBuilder().sources(java.util.List.of(source.toBuilder().mappings(java.util.List.of(company)).build())).build();
        for(var code:java.util.List.of("01","02","03","04")) {
            var snapshot=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),java.util.List.of(
                    new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),java.util.List.of(java.util.Map.of("id",1,"corporationCode",code)))),1);
            assertEquals("0"+code,new SyncFieldMapper().transform(d,snapshot,java.util.List.of()).getFirst().fields().get("companyCode"));
        }
        var unknown=new MysqlSyncReader.Snapshot(java.time.LocalDateTime.now(),java.util.List.of(
                new MysqlSyncReader.SourceRows(source.object(),source.sourceObject(),java.util.List.of(java.util.Map.of("id",1,"corporationCode","05")))),1);
        assertThrows(IllegalArgumentException.class,()->new SyncFieldMapper().transform(d,unknown,java.util.List.of()));
    }

    @Test void mapsTheRegularExecutionOrderHeaderWithoutSourceWrites() {
        var definition = DppmsExecutionOrderSyncTemplate.create(9L);
        assertEquals("DPPMS_CRM_EXECUTION_ORDER", definition.adapter());
        assertEquals("RETAIN", definition.missingPolicy());
        assertEquals("UPSERT", definition.loadingMode());
        var source = definition.sources().getFirst();
        assertEquals("pm_project_property_from_sms", source.sourceObject());
        assertEquals("id", source.sourceKey());
        assertEquals("TABLE", source.readMode());
        assertEquals("pm_project_property_from_sms", source.table());
        var sql = MysqlSyncReader.compile(source).sql();
        assertTrue(sql.contains("orderExecNumber"));
        assertTrue(sql.contains("pm_project_property_from_sms"));
        assertTrue(source.mappings().stream().anyMatch(m -> m.target().equals("executionNo") && m.source().equals("orderExecNumber")));
        assertTrue(source.mappings().stream().anyMatch(m -> m.target().equals("applyType") && m.source().equals("applyType")));
        assertEquals(31, source.mappings().size());
    }
}
