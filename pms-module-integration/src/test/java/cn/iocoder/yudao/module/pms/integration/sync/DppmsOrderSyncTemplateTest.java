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
    @Test void compilesReadOnlyPagesAndRetainsRawIdsAndVersions() {
        var definition=DppmsOrderSyncTemplate.create(12L);
        for(var source:definition.sources()) {
            var sql=MysqlSyncReader.compile(source);
            assertTrue(sql.sql().contains("r.*"));
            assertTrue(sql.sql().contains("payloadVariants"));
            assertEquals("id",source.sourceKey());
            assertFalse(source.syncPrimaryKey());
            assertEquals(0,sql.values().size());
            assertFalse(sql.sql().contains(":afterId"));
            var page=MysqlSyncReader.pageSql(source,new SyncPagingState(0,7,java.util.List.of(99L),0,java.time.LocalDateTime.now()),17);
            assertEquals(java.util.List.of(7L,99L,17),page.values());
            assertTrue(page.sql().endsWith("ORDER BY `id` LIMIT ?"));
        }
        assertTrue(definition.autoPaging());
        assertEquals("RETAIN",definition.missingPolicy());
        assertEquals("ONCE",definition.mode());
    }
}
