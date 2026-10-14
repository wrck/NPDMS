package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reuse only an already-evidenced, complete-source-equivalent source and verified unchanged target. */
final class SyncEvidenceSelection {
    record Key(String object,String sourceKey) {}
    record Selected(MysqlSyncReader.Snapshot snapshot,Set<Key> reused) {
        List<DataSyncAdapter.Change> needingEvidence(List<DataSyncAdapter.Change> changes) {
            return changes.stream().filter(change->!reused.contains(new Key(change.object(),change.sourceKey()))).toList();
        }
    }
    static Selected select(SyncDefinition definition,MysqlSyncReader.Snapshot snapshot,
                           List<DataSyncAdapter.Change> changes,List<SyncBindingDO> bindings,boolean preview) {
        return select(definition,snapshot,changes,bindings,preview,List.of());
    }
    /** Chunk-local facts from bindingFacts may be reused only by the built-in table engine,
     * whose mapping and normalization paths read, but do not mutate, these decoded values.
     * Other adapters retain independent decoding; no cache survives this selection call. */
    static Selected select(SyncDefinition definition,MysqlSyncReader.Snapshot snapshot,
                           List<DataSyncAdapter.Change> changes,List<SyncBindingDO> bindings,boolean preview,
                           List<DataSyncAdapter.Binding> decodedBindings) {
        if(!"REUSE_UNCHANGED".equals(definition.effectiveEvidencePolicy()))return new Selected(snapshot,Set.of());
        Map<Key,DataSyncAdapter.Binding> decoded=new HashMap<>();
        if("TABLE_MAPPING".equals(definition.adapter()) && decodedBindings!=null)
            decodedBindings.forEach(binding->decoded.put(new Key(binding.object(),binding.sourceKey()),binding));
        Map<Key,SyncBindingDO> prior=new HashMap<>();
        bindings.forEach(binding->prior.put(new Key(binding.getObjectKey(),binding.getSourceKey()),binding));
        Map<Key,DataSyncAdapter.Change> results=new HashMap<>();
        changes.forEach(change->results.put(new Key(change.object(),change.sourceKey()),change));
        Map<String,SyncDefinition.Source> sources=new HashMap<>();
        definition.sources().forEach(source->sources.put(source.object(),source));
        Set<Key> reused=new HashSet<>();
        List<MysqlSyncReader.SourceRows> selected=new ArrayList<>();long bytes=0;
        for(var object:snapshot.objects()) {
            var source=sources.get(object.object());List<Map<String,Object>> rows=new ArrayList<>();
            for(var row:object.rows()) {
                var key=new Key(object.object(),row.get(source.sourceKey()).toString());
                var binding=prior.get(key);var change=results.get(key);
                boolean reuse=false;
                if(binding!=null && binding.getLastRunId()!=null && Boolean.TRUE.equals(binding.getTargetShared()) && change!=null && "UNCHANGED".equals(change.action())
                        && Objects.equals(binding.getSourceObject(),object.sourceObject())
                        && change.targetId()!=null && change.targetId().equals(binding.getTargetId())) {
                    var cached=decoded.get(key);
                    var fields=cached!=null && cached.lastFields()!=null
                            && Objects.equals(cached.targetId(),binding.getTargetId())
                            ?cached.lastFields():SyncBindingSnapshots.decode(binding.getFieldsJson());
                    var after=change.after();
                    if(preview && after.containsKey("_previewVirtualIds")) {
                        after=new LinkedHashMap<>(after);after.remove("_previewVirtualIds");
                    }
                    reuse=fields.get("_syncSourcePayload") instanceof Map<?,?> original
                            && SyncBindingSnapshots.same(original,row) && sameDerivedFacts(source,fields,after,
                                    Objects.equals(after.get("_syncSourcePayload"),row));
                }
                if(reuse)reused.add(key);
                else { rows.add(row);bytes+=JsonUtils.toJsonString(row).getBytes(StandardCharsets.UTF_8).length; }
            }
            if(!rows.isEmpty())selected.add(new MysqlSyncReader.SourceRows(object.object(),object.sourceObject(),List.copyOf(rows)));
        }
        return new Selected(new MysqlSyncReader.Snapshot(snapshot.upper(),List.copyOf(selected),bytes),Set.copyOf(reused));
    }
    /** Normalize only declared JSON and proven JDBC temporal mapping representations.
     * Original source fields, ordinary strings and unproven legacy facts remain exact. */
    private static boolean sameDerivedFacts(SyncDefinition.Source source,Map<String,Object> left,Map<String,Object> right,
                                            boolean verifiedSourcePayload) {
        if(Objects.equals(left,right))return true;
        if(source.targets()==null)return SyncBindingSnapshots.same(left,right);
        Map<String,Object> normalizedLeft=new LinkedHashMap<>(left),normalizedRight=alignedCopy(left,right);
        // The original payload was already compared with the incoming source above.
        // Skip its duplicate nested comparison only when the adapter's after-payload
        // is also exactly that incoming row. All flattened fields and target facts
        // remain compared, and any changed/missing after-payload takes the full path.
        if(verifiedSourcePayload) {
            normalizedLeft.remove("_syncSourcePayload");normalizedRight.remove("_syncSourcePayload");
        }
        try {
            for(var step:source.targets())for(var mapping:step.mappings()) {
                String key=step.name()+"."+mapping.target();
                if("JSON".equals(mapping.conversion())) {
                    Object originalLeft=left.get(key),originalRight=right.get(key);
                    Object parsedLeft=originalLeft instanceof String value?parseJsonFact(value):originalLeft;
                    Object parsedRight=Objects.equals(originalLeft,originalRight)?parsedLeft:
                            originalRight instanceof String value?parseJsonFact(value):originalRight;
                    if(originalLeft instanceof String)normalizedLeft.put(key,parsedLeft);
                    if(originalRight instanceof String)normalizedRight.put(key,parsedRight);
                    // Equal JSON trees may retain different object-property order. Reuse the
                    // equivalent local node only inside these comparison copies; never mutate it.
                    if(parsedLeft instanceof tools.jackson.databind.JsonNode
                            && parsedRight instanceof tools.jackson.databind.JsonNode
                            && Objects.equals(parsedLeft,parsedRight))normalizedRight.put(key,parsedLeft);
                } else {
                    // JDBC DATE/TIMESTAMP serialize differently from the java.time values
                    // originally produced by the declared mapping. Normalize only a proven
                    // equivalent pair, leaving already-matching legacy representations intact.
                    Object current=right.get(key),canonical=current;
                    if("DATE".equals(mapping.conversion()) && current instanceof java.sql.Date date)
                        canonical=date.toLocalDate();
                    else if("DATETIME".equals(mapping.conversion()) && current instanceof java.sql.Timestamp timestamp
                            && timestamp.getNanos()%1_000_000==0)
                        canonical=timestamp.toLocalDateTime();
                    if(canonical!=current && !SyncBindingSnapshots.same(left.get(key),current)
                            && SyncBindingSnapshots.same(left.get(key),canonical))normalizedRight.put(key,canonical);
                }
            }
            if(normalizedRight.containsKey("_targets"))
                normalizedRight.put("_targets",alignedTargetReferences(normalizedLeft.get("_targets"),normalizedRight.get("_targets")));
            if(SyncBindingSnapshots.same(normalizedLeft,normalizedRight))return true;
        } catch(RuntimeException invalidJson) {
            // Normalization cannot prove equivalence; the original exact rule may still do so.
        }
        // The accepted facts remain exact-equivalent OR declared-normalization-equivalent.
        // Try normalization first to avoid serializing a large snapshot twice on every replay.
        return SyncBindingSnapshots.same(left,right);
    }

    /** Preserve every key/value (including null and extra fields) while aligning only
     * object-property order. JSON object order is already ignored by the tree fallback. */
    private static Map<String,Object> alignedCopy(Map<String,Object> left,Map<String,Object> right) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:left.keySet())if(right.containsKey(key))result.put(key,right.get(key));
        right.forEach((key,value)->{if(!left.containsKey(key))result.put(key,value);});
        return result;
    }

    /** Align only object-property order inside the existing ordered target-reference list.
     * Do not reorder references, change values or discard extra/missing/null fields. */
    @SuppressWarnings("unchecked")
    private static Object alignedTargetReferences(Object left,Object right) {
        if(!(left instanceof List<?> leftRefs) || !(right instanceof List<?> rightRefs)
                || leftRefs.size()!=rightRefs.size())return right;
        List<Object> result=new ArrayList<>(rightRefs.size());
        for(int index=0;index<rightRefs.size();index++) {
            Object l=leftRefs.get(index),r=rightRefs.get(index);
            if(l instanceof Map<?,?> lm && r instanceof Map<?,?> rm
                    && lm.keySet().stream().allMatch(String.class::isInstance)
                    && rm.keySet().stream().allMatch(String.class::isInstance))
                result.add(alignedCopy((Map<String,Object>)lm,(Map<String,Object>)rm));
            else result.add(r);
        }
        return result;
    }

    private static Object parseJsonFact(String value) {
        var tree=JsonUtils.parseTree(value);
        if(tree==null || tree.isMissingNode())throw new IllegalArgumentException("Missing JSON fact");
        return tree;
    }

}
