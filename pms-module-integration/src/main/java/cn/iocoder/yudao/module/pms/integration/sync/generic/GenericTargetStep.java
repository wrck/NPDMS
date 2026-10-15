package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import java.util.List;
import java.util.Map;

/** A row pipeline. References $step.id resolve only to earlier steps of the same source row. */
public record GenericTargetStep(String name, String table, List<String> keys, String mode,
        String nullPolicy, List<String> updateColumns, String whenField, String newerBy, String tieBreaker,
        List<SyncDefinition.Mapping> mappings, List<Lookup> lookups, List<String> sameVersionColumns) {
    public GenericTargetStep withKeys(List<String> businessKeys) {
        return new GenericTargetStep(name,table,List.copyOf(businessKeys),mode,nullPolicy,updateColumns,
                whenField,newerBy,tieBreaker,mappings,lookups,sameVersionColumns);
    }
    /** Existing task definitions do not opt into source-version payload checks. */
    public GenericTargetStep(String name, String table, List<String> keys, String mode,
            String nullPolicy, List<String> updateColumns, String whenField, String newerBy, String tieBreaker,
            List<SyncDefinition.Mapping> mappings, List<Lookup> lookups) {
        this(name, table, keys, mode, nullPolicy, updateColumns, whenField, newerBy, tieBreaker,
                mappings, lookups, null);
    }
    public record Lookup(String table, Map<String,String> match, Map<String,String> outputs,
                         String onMissing) {}
}
