package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MysqlSyncReaderCompositeKeyTest {
    SyncDefinition.Source source() { return SyncDefinition.Source.builder().sourceKey("rowKey")
            .sourceKeyColumns(List.of("projectCode","memo","sourceProjectId")).build(); }
    Map<String,Object> row(Object memo,String project) {
        var row=new LinkedHashMap<String,Object>();row.put("projectCode","中文");row.put("memo",memo);row.put("sourceProjectId",project);return row;
    }
    @Test void originalDuplicateRowsKeepSeparateKeysAndReorderingDoesNotChangeTheirIdentity() {
        var keys=new MysqlSyncReader.CompositeRowKeys(source());var first=row(null,"101");var duplicate=row(null,"101");
        keys.assign(first);keys.assign(duplicate);
        assertTrue(first.get("rowKey").toString().matches("[0-9a-f]{64}:1"));
        // Exact key produced by the existing Python collector's sorted UTF-8 JSON encoding.
        assertEquals("b23a56d27b0539d7867f5d4b9d26725a18ab19ce67dd79bdd793e97233fb9033:1",first.get("rowKey"));
        assertEquals(first.get("rowKey").toString().replace(":1",":2"),duplicate.get("rowKey"));
        var reloaded=row(null,"101");new MysqlSyncReader.CompositeRowKeys(source()).assign(reloaded);assertEquals(first.get("rowKey"),reloaded.get("rowKey"));
        var empty=row("","101");keys.assign(empty);assertNotEquals(first.get("rowKey"),empty.get("rowKey"));
        var otherProject=row(null,"102");keys.assign(otherProject);assertNotEquals(first.get("rowKey"),otherProject.get("rowKey"));
    }
    @Test void missingIdentityColumnsAndReplacingAnExistingColumnAreRejected() {
        var keys=new MysqlSyncReader.CompositeRowKeys(source());var missing=row(null,"101");missing.remove("memo");assertThrows(IllegalArgumentException.class,()->keys.assign(missing));
        var existing=row(null,"101");existing.put("rowKey","old");assertThrows(IllegalArgumentException.class,()->keys.assign(existing));
        assertThrows(IllegalArgumentException.class,()->new MysqlSyncReader.CompositeRowKeys(source().toBuilder().sourceKeyColumns(List.of("memo","memo")).build()));
    }
}
