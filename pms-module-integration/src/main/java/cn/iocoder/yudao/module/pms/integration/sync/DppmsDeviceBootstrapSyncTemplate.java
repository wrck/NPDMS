package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;

public final class DppmsDeviceBootstrapSyncTemplate {
    static final String ADAPTER="DPPMS_DEVICE_BOOTSTRAP",BASE="DEVICE_BASE";
    static final List<String> COLUMNS=List.of("deviceSn","shipmentId","shipmentSourceKey","contractNo","packageNo",
            "shipmentTime","productCode","productModel","productName");
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("设备基础来源连接无效");
        var source=SyncDefinition.Source.builder().object(BASE).sourceObject("ast_device_shipment").readMode("LOCAL_OWNER")
                .sourceKey("deviceSn").columns(List.of()).filters(List.of()).parameters(Map.of())
                .mappings(COLUMNS.stream().map(n->SyncDefinition.Mapping.builder().source(n).target(n).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .retryCount(0).retryIntervalSeconds(60).readStrategy("STREAMING_CURSOR").chunkSize(500).fetchSize(500)
                .maxRows(500).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
    private DppmsDeviceBootstrapSyncTemplate() {}
}
