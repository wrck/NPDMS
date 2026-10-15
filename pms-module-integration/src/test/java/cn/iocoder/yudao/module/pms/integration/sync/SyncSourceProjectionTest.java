package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SyncSourceProjectionTest {
    @Test void nestedJsonColumnsReplayAsJdbcTextAndReachCustomInfoOwner() {
        Map<String,Object> fields = Map.of("jsonText", "{\"existing\":null}", "plain", "普通字符串", "number", 42);
        var expanded = expand("""
                [{"object":"master","fields":{"id":12,
                "customInfo":{"中文":"引号\\\"","nested":{"value":null},"items":[1,null,{"ok":true}]},
                "array":[{"nested":{"value":null}},null,["中文","\\\""]],
                "jsonText":"{\\\"existing\\\":null}","plain":"普通字符串","number":42,"nothing":null,
                "isemail":1,"effectiveFrom":"2016-01-01T00:00:00","effectiveTo":null,"defaultPage":"/legacy","pwdoverdue":null}}]
                """)
                .getFirst().rows().getFirst();
        assertInstanceOf(String.class, expanded.get("customInfo"));
        assertInstanceOf(String.class, expanded.get("array"));
        var expectedCustom = JsonUtils.parseTree("{\"中文\":\"引号\\\"\",\"nested\":{\"value\":null},\"items\":[1,null,{\"ok\":true}]}");
        assertEquals(expectedCustom, JsonUtils.parseTree((String)expanded.get("customInfo")));
        assertEquals(JsonUtils.parseTree("[{\"nested\":{\"value\":null}},null,[\"中文\",\"\\\"\"]]"), JsonUtils.parseTree((String)expanded.get("array")));
        for (var name : List.of("jsonText", "plain", "number")) assertEquals(fields.get(name), expanded.get(name));
        assertTrue(expanded.containsKey("nothing")); assertNull(expanded.get("nothing"));
        var result = DppmsUserCustomInfo.assemble(expanded, null, List.of());
        assertEquals(expectedCustom, result.path("customInfo"));
    }
    private SyncDefinition definition() {
        var root=SyncDefinition.Source.builder().object("master").sourceObject("header").sourceKey("id")
                .readMode("SQL").sql("SELECT 1 AS _groupKey, '[]' AS _sourceRows")
                .groupKey("_groupKey").rowProjection("_sourceRows").build();
        var alias=SyncDefinition.Source.builder().object("alias").sourceObject("original").sourceKey("originalId")
                .readMode("SQL").queryRef("master").build();
        return SyncDefinition.builder().mode("ONCE").sources(List.of(root,alias)).build();
    }
    private List<MysqlSyncReader.SourceRows> expand(String json) {
        var d=definition();return SyncSourceProjection.expand(new SyncSourceProjection.Input(d,d.sources().getFirst(),
                List.of(Map.of("_groupKey",12,"_sourceRows",json))));
    }
    @Test void oneGroupRetainsBothSourceIdentitiesAndCanonicalKey() {
        var rows=expand("""
                [{"object":"master","fields":{"id":12,"name":null}},null,
                 {"object":"alias","fields":{"originalId":11}}]
                """);
        assertEquals(List.of("master","alias"),rows.stream().map(MysqlSyncReader.SourceRows::object).toList());
        assertNull(rows.getFirst().rows().getFirst().get("name"));
        assertEquals(12,rows.getLast().rows().getFirst().get("_groupKey"));
        assertEquals(11,rows.getLast().rows().getFirst().get("originalId"));
    }
    @Test void emptyScopeNeverProducesAnUnfilteredReferenceRead() {
        var d=definition();assertDoesNotThrow(()->SyncSourceProjection.validate(d));
        assertTrue(SyncSourceProjection.reference(d.sources().getLast()));
        var rows=SyncSourceProjection.expand(new SyncSourceProjection.Input(d,d.sources().getFirst(),List.of()));
        assertEquals(2,rows.size());assertTrue(rows.stream().allMatch(s->s.rows().isEmpty()));
    }
    @Test void unknownObjectsDuplicateKeysAndOverwrittenCanonicalIdentityFailClosed() {
        assertThrows(IllegalArgumentException.class,()->expand("[{\"object\":\"foreign\",\"fields\":{\"id\":1}}]"));
        assertThrows(IllegalArgumentException.class,()->expand("[{\"object\":\"alias\",\"fields\":{\"originalId\":1}},{\"object\":\"alias\",\"fields\":{\"originalId\":1}}]"));
        assertThrows(IllegalArgumentException.class,()->expand("[{\"object\":\"alias\",\"fields\":{\"originalId\":1,\"_groupKey\":99}}]"));
        assertThrows(IllegalArgumentException.class,()->expand("[{\"object\":\"alias\",\"fields\":{}}]"));
    }
    @Test void referenceCannotSupplyASecondSqlOrWiderScope() {
        var d=definition();d.sources().getLast().sql("SELECT * FROM original");
        assertThrows(IllegalArgumentException.class,()->SyncSourceProjection.validate(d));
        var widened=definition();widened.sources().getLast().parameters(Map.of("projectIds",""));
        assertThrows(IllegalArgumentException.class,()->SyncSourceProjection.validate(widened));
    }
}
