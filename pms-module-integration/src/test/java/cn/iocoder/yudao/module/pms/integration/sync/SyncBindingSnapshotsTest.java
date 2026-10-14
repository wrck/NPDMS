package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SyncBindingSnapshotsTest {
    private Map<String,Object> largeFields() {
        Map<String,Object> source=new LinkedHashMap<>();
        source.put("id",1);source.put("barcode","DEVICE-001");
        source.put("note","来源事实，不可遗漏。".repeat(200));
        Map<String,Object> fields=new LinkedHashMap<>(source);
        fields.put("_syncSourcePayload",source);
        fields.put("shipment.source_payload",JsonUtils.toJsonString(source));
        fields.put("device.sn","DEVICE-001");fields.put("shipment.device_sn","DEVICE-001");
        fields.put("_targets",List.of(Map.of("table","ast_device_shipment","id",123L)));
        return fields;
    }
    @Test void largeSnapshotUsesSmallerLosslessStorageWithoutChangingPublicFields() {
        var fields=largeFields();String encoded=SyncBindingSnapshots.encode(fields,true);
        assertEquals("NPDMS_SYNC_BINDING_Z1",JsonUtils.parseMap(encoded).get("$format"));
        assertTrue(encoded.getBytes(StandardCharsets.UTF_8).length<JsonUtils.toJsonString(fields).getBytes(StandardCharsets.UTF_8).length/2);
        assertTrue(SyncBindingSnapshots.same(fields,SyncBindingSnapshots.decode(encoded)));
    }
    @Test void fullPolicyAndSmallSnapshotsKeepTheirExistingShape() {
        var large=largeFields();assertEquals(JsonUtils.toJsonString(large),SyncBindingSnapshots.encode(large,false));
        var raw=Map.<String,Object>of("id",1,"name","name");
        var small=new LinkedHashMap<>(raw);small.put("_syncSourcePayload",raw);
        String encoded=SyncBindingSnapshots.encode(small,true);
        assertEquals("NPDMS_SYNC_BINDING_V1",JsonUtils.parseMap(encoded).get("$format"));
        assertTrue(SyncBindingSnapshots.same(small,SyncBindingSnapshots.decode(encoded)));
    }
    @Test void legacyAndV1SnapshotsRemainReadable() {
        String legacy="{\"id\":1,\"_syncSourcePayload\":{\"id\":1},\"target.id\":2}";
        String v1="{\"$format\":\"NPDMS_SYNC_BINDING_V1\",\"source\":{\"id\":1},\"derived\":{\"target.id\":2}}";
        assertTrue(SyncBindingSnapshots.same(SyncBindingSnapshots.decode(legacy),SyncBindingSnapshots.decode(v1)));
    }
    @Test void invalidCompressedDataFailsInsteadOfReturningFalseFacts() {
        assertThrows(IllegalArgumentException.class,()->SyncBindingSnapshots.decode("{\"$format\":\"NPDMS_SYNC_BINDING_Z1\",\"size\":100,\"data\":\"not base64\"}"));
        assertThrows(IllegalArgumentException.class,()->SyncBindingSnapshots.decode("{\"$format\":\"NPDMS_SYNC_BINDING_Z1\",\"size\":2147483647,\"data\":\"AA==\"}"));
    }
    @Test void wrongLengthAndTruncatedCompressedStreamAreRejected() {
        var encoded=JsonUtils.parseMap(SyncBindingSnapshots.encode(largeFields(),true));
        assertEquals("NPDMS_SYNC_BINDING_Z1",encoded.get("$format"));
        var wrong=new LinkedHashMap<>(encoded);wrong.put("size",((Number)wrong.get("size")).intValue()-1);
        assertThrows(IllegalArgumentException.class,()->SyncBindingSnapshots.decode(JsonUtils.toJsonString(wrong)));
        byte[] data=Base64.getDecoder().decode((String)encoded.get("data"));
        encoded.put("data",Base64.getEncoder().encodeToString(Arrays.copyOf(data,data.length-3)));
        assertThrows(IllegalArgumentException.class,()->SyncBindingSnapshots.decode(JsonUtils.toJsonString(encoded)));
    }
    @Test void sourceReservedLookingKeysAndNullOverrideStillRoundTrip() {
        var fields=largeFields();var source=new LinkedHashMap<String,Object>((Map<String,Object>)fields.get("_syncSourcePayload"));
        source.put("$format","NPDMS_SYNC_BINDING_Z1");source.put("size",123);source.put("data","ordinary source text");
        fields.putAll(source);fields.put("_syncSourcePayload",source);
        assertTrue(SyncBindingSnapshots.same(fields,SyncBindingSnapshots.decode(SyncBindingSnapshots.encode(fields,true))));
        fields.put("barcode",null);
        assertTrue(SyncBindingSnapshots.same(fields,SyncBindingSnapshots.decode(SyncBindingSnapshots.encode(fields,true))));
        assertNull(SyncBindingSnapshots.decode(SyncBindingSnapshots.encode(fields,true)).get("barcode"));
    }
    @Test void identicalSerializedFactsDoNotNeedASecondParse() {
        var left=Map.<String,Object>of("id",1,"nested",List.of(Map.of("id",2)));
        var right=Map.<String,Object>of("id",1L,"nested",List.of(Map.of("id",2L)));
        assertNotEquals(left,right);
        try (var json=org.mockito.Mockito.mockStatic(JsonUtils.class,org.mockito.Mockito.CALLS_REAL_METHODS)) {
            assertTrue(SyncBindingSnapshots.same(left,right));
            json.verify(()->JsonUtils.parseTree(org.mockito.ArgumentMatchers.anyString()),org.mockito.Mockito.never());
        }
    }
    @Test void textualShortcutKeepsStructuralAndStrictFactComparison() {
        var left=new LinkedHashMap<String,Object>();left.put("id",1);left.put("name","same");
        var reordered=new LinkedHashMap<String,Object>();reordered.put("name","same");reordered.put("id",1L);
        assertNotEquals(JsonUtils.toJsonString(left),JsonUtils.toJsonString(reordered));
        assertTrue(SyncBindingSnapshots.same(left,reordered));
        assertFalse(SyncBindingSnapshots.same(Map.of("id",1),Map.of("id","1")));
        assertFalse(SyncBindingSnapshots.same(List.of(1,2),List.of(2,1)));
        assertFalse(SyncBindingSnapshots.same(Map.of("v","{\"a\":1}"),Map.of("v","{ \"a\": 1 }")));
        var optional=new LinkedHashMap<>(left);optional.put("optional",null);
        boolean original=Objects.equals(JsonUtils.parseTree(JsonUtils.toJsonString(optional)),JsonUtils.parseTree(JsonUtils.toJsonString(left)));
        assertEquals(original,SyncBindingSnapshots.same(optional,left));
    }

}
