package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Complete original relationship records; unresolved self relations must not disappear in SQL. */
public final class DppmsDeviceRelationshipSyncTemplate {
    private DppmsDeviceRelationshipSyncTemplate() {}
    static final String ADAPTER="DPPMS_DEVICE_RELATIONSHIP";
    static final String OBJECT="fb_shipment_barcode_relation";
    static final List<String> ORIGINAL_COLUMNS=List.of("id","sn1","item1","sn2","item2","contract","createtime","updatetime");
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0) throw new IllegalArgumentException("主附SN来源连接无效");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).readMode("TABLE").table(OBJECT)
                .sourceKey("id").updatedAt(null).columns(ORIGINAL_COLUMNS).filters(List.of()).parameters(Map.of())
                .mappings(ORIGINAL_COLUMNS.stream().map(name->SyncDefinition.Mapping.builder()
                        .source(name).target(name).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false).readStrategy("STREAMING_CURSOR")
                .fetchSize(1000).chunkSize(500).restartPolicy("RESTART_ALL").queryTimeoutSeconds(0)
                .maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
