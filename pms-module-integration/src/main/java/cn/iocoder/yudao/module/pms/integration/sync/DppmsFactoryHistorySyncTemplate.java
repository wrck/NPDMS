package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;

public final class DppmsFactoryHistorySyncTemplate {
    static final String ADAPTER="DPPMS_FACTORY_HISTORY",OBJECT="fb_soft_version";
    static final List<String> COLUMNS=List.of("serial_number","conp","cpld","boot","pcb");
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("出厂版本来源连接无效");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).readMode("TABLE").table(OBJECT)
                .sourceKey("factoryRowKey").sourceKeyColumns(COLUMNS).columns(COLUMNS).filters(List.of())
                .mappings(COLUMNS.stream().map(name->SyncDefinition.Mapping.builder().source(name).target(name).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .retryCount(0).retryIntervalSeconds(60).readStrategy("SNAPSHOT").maxRows(10000).maxBytes(64L*1024*1024)
                .sources(List.of(source)).build();
    }
    private DppmsFactoryHistorySyncTemplate() {}
}
