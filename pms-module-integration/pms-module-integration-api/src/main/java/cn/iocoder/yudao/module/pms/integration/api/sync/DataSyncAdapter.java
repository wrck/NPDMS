package cn.iocoder.yudao.module.pms.integration.api.sync;

import java.util.List;
import java.util.Map;

/** Local transactional business sink; implementations never perform external writes. */
public interface DataSyncAdapter {
    Descriptor descriptor();
    List<Change> preview(Batch batch);
    List<Change> apply(Batch batch);
    void refreshCaches();
    /** Tree adapters need historical parents; flat page adapters resolve parents through their Owner. */
    default boolean requiresAllBindings() { return true; }
    /** Canonical business records may combine equivalent legacy rows; source identities stay distinct. */
    default boolean sharesTargetAcrossSources() { return false; }

    record Field(String name, String label, String type, boolean required) {}
    record ObjectDescriptor(String name, String label, List<Field> fields,
                            String targetContext, String targetObjectType, String targetTable,
                            boolean supportsSourcePrimaryKey) {}
    record Descriptor(String key, String label, List<ObjectDescriptor> objects, List<String> missingPolicies,
                      List<String> loadingModes,boolean supportsTargetClear) {}
    /** fields._sourcePrimaryKey requests an exact target ID; targetId remains the established binding. */
    record Row(String object, String sourceKey, Map<String, Object> fields, Long targetId) {}
    record Binding(String object, String sourceKey, Long targetId, Map<String, Object> lastFields) {}
    record Batch(String owner, List<Row> rows, List<Binding> bindings, boolean full,
                 String missingPolicy, boolean adoptExisting, String loadingMode,boolean clearBeforeLoad) {}
    record Change(String object, String sourceKey, Long targetId, String action,
                  Map<String, Object> before, Map<String, Object> after, String message) {}
}
