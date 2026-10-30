package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DppmsExecutionOrderSyncTemplateTest {
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
