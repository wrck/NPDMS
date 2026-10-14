package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Lossless storage codec; all callers continue to see the established flattened fields shape. */
public final class SyncBindingSnapshots {
    private static final String FORMAT = "NPDMS_SYNC_BINDING_V1";
    private static final String COMPRESSED = "NPDMS_SYNC_BINDING_Z1";
    // Only an encoding budget: larger snapshots retain their existing lossless V1 shape.
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private SyncBindingSnapshots() {}

    public static Map<String,Object> decode(String json) {
        Map<String,Object> stored=JsonUtils.parseMap(json);
        if(stored==null)throw new IllegalArgumentException("Invalid binding snapshot JSON");
        if(COMPRESSED.equals(stored.get("$format")) && stored.keySet().equals(Set.of("$format","size","data")))
            stored=inflate(stored);
        if(!FORMAT.equals(stored.get("$format")) || stored.size()!=3
                || !(stored.get("source") instanceof Map<?,?> source)
                || !(stored.get("derived") instanceof Map<?,?> derived))return stored;
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,value)->result.put(key.toString(),value));
        derived.forEach((key,value)->result.put(key.toString(),value));
        result.put("_syncSourcePayload",source);
        return result;
    }

    public static String encode(Map<String,Object> fields,boolean compact) {
        if(!compact || !(fields.get("_syncSourcePayload") instanceof Map<?,?> source))
            return JsonUtils.toJsonString(fields);
        // JsonUtils omits null map values. A null overriding a non-null source value
        // cannot be represented by the derived object without resurrecting that source value.
        if(fields.entrySet().stream().anyMatch(entry->entry.getValue()==null && source.get(entry.getKey())!=null))
            return JsonUtils.toJsonString(fields);
        Map<String,Object> derived=new LinkedHashMap<>();
        fields.forEach((key,value)->{
            if(!"_syncSourcePayload".equals(key) && (!source.containsKey(key) || !Objects.equals(source.get(key),value)))
                derived.put(key,value);
        });
        // Compact encoding is only lossless when every original source field is still represented.
        if(source.keySet().stream().anyMatch(key->!fields.containsKey(key)))return JsonUtils.toJsonString(fields);
        Map<String,Object> stored=new LinkedHashMap<>();
        stored.put("$format",FORMAT);stored.put("source",source);stored.put("derived",derived);
        return compressIfSmaller(JsonUtils.toJsonString(stored));
    }

    private static String compressIfSmaller(String json) {
        byte[] raw=json.getBytes(StandardCharsets.UTF_8);
        if(raw.length<1024 || raw.length>MAX_COMPRESSED_BYTES)return json;
        var deflater=new Deflater(Deflater.BEST_SPEED);
        try {
            var output=new ByteArrayOutputStream();
            try(var stream=new DeflaterOutputStream(output,deflater)){stream.write(raw);}
            var envelope=new LinkedHashMap<String,Object>();
            envelope.put("$format",COMPRESSED);envelope.put("size",raw.length);
            envelope.put("data",Base64.getEncoder().encodeToString(output.toByteArray()));
            String encoded=JsonUtils.toJsonString(envelope);
            return encoded.length()<raw.length?encoded:json; // Envelope is entirely ASCII.
        } catch(IOException impossible) {
            throw new IllegalStateException("Could not encode binding snapshot",impossible);
        } finally {deflater.end();}
    }

    private static Map<String,Object> inflate(Map<String,Object> envelope) {
        if(!(envelope.get("size") instanceof Integer size) || size<1 || size>MAX_COMPRESSED_BYTES
                || !(envelope.get("data") instanceof String encoded) || encoded.length()>size)
            throw new IllegalArgumentException("Invalid compressed binding snapshot metadata");
        try(var input=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            // A corrupt length or compression bomb must fail before allocating unbounded output.
            byte[] raw=input.readNBytes(size+1);
            if(raw.length!=size)throw new IllegalArgumentException("Binding snapshot length mismatch");
            var restored=JsonUtils.parseMap(new String(raw,StandardCharsets.UTF_8));
            if(restored==null || !FORMAT.equals(restored.get("$format")) || restored.size()!=3
                    || !(restored.get("source") instanceof Map<?,?>) || !(restored.get("derived") instanceof Map<?,?>))
                throw new IllegalArgumentException("Invalid compressed binding snapshot content");
            return restored;
        } catch(IOException invalid) {
            throw new IllegalArgumentException("Corrupt compressed binding snapshot",invalid);
        }
    }

    static boolean same(Object left,Object right) {
        if(Objects.equals(left,right))return true;
        String leftJson=JsonUtils.toJsonString(left),rightJson=JsonUtils.toJsonString(right);
        // Equal serializer output already proves equal JSON trees. Keep the structural
        // fallback for object key order and all existing serializer/number semantics.
        return Objects.equals(leftJson,rightJson)
                || Objects.equals(JsonUtils.parseTree(leftJson),JsonUtils.parseTree(rightJson));
    }
}
