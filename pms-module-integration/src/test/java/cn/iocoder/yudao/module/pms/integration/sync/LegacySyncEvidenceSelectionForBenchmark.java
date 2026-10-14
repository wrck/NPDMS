package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reuse only an already-evidenced, complete-source-equivalent source and verified unchanged target. */
final class LegacySyncEvidenceSelectionForBenchmark {
    record Key(String object,String sourceKey) {}
    record Selected(MysqlSyncReader.Snapshot snapshot,Set<Key> reused) {
        List<DataSyncAdapter.Change> needingEvidence(List<DataSyncAdapter.Change> changes) {
            return changes.stream().filter(change->!reused.contains(new Key(change.object(),change.sourceKey()))).toList();
        }
    }
    static Selected select(SyncDefinition definition,MysqlSyncReader.Snapshot snapshot,
                           List<DataSyncAdapter.Change> changes,List<SyncBindingDO> bindings,boolean preview) {
        if(!"REUSE_UNCHANGED".equals(definition.effectiveEvidencePolicy()))return new Selected(snapshot,Set.of());
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
                    var fields=SyncBindingSnapshots.decode(binding.getFieldsJson());
                    var after=change.after();
                    if(preview && after.containsKey("_previewVirtualIds")) {
                        after=new LinkedHashMap<>(after);after.remove("_previewVirtualIds");
                    }
                    reuse=fields.get("_syncSourcePayload") instanceof Map<?,?> original
                            && SyncBindingSnapshots.same(original,row) && sameDerivedFacts(source,fields,after);
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
    private static boolean sameDerivedFacts(SyncDefinition.Source source,Map<String,Object> left,Map<String,Object> right) {
        if(Objects.equals(left,right))return true;
        if(source.targets()==null)return SyncBindingSnapshots.same(left,right);
        Map<String,Object> normalizedLeft=new LinkedHashMap<>(left),normalizedRight=new LinkedHashMap<>(right);
        try {
            for(var step:source.targets())for(var mapping:step.mappings()) {
                String key=step.name()+"."+mapping.target();
                if("JSON".equals(mapping.conversion())) {
                    if(left.get(key) instanceof String value)normalizedLeft.put(key,parseJsonFact(value));
                    if(right.get(key) instanceof String value)normalizedRight.put(key,parseJsonFact(value));
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
            if(SyncBindingSnapshots.same(normalizedLeft,normalizedRight))return true;
        } catch(RuntimeException invalidJson) {
            // Normalization cannot prove equivalence; the original exact rule may still do so.
        }
        // The accepted facts remain exact-equivalent OR declared-normalization-equivalent.
        // Try normalization first to avoid serializing a large snapshot twice on every replay.
        return SyncBindingSnapshots.same(left,right);
    }

    private static Object parseJsonFact(String value) {
        var tree=JsonUtils.parseTree(value);
        if(tree==null || tree.isMissingNode())throw new IllegalArgumentException("Missing JSON fact");
        return tree;
    }

}
