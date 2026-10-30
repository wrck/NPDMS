package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DppmsOrderSyncTemplateTest {
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
