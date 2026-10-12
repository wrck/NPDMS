package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Entire original table is read, including the explicitly excluded empty record. */
public final class DppmsDictionaryDataSyncTemplate {
    private DppmsDictionaryDataSyncTemplate() {}
    static final String ADAPTER="DPPMS_DICTIONARY_DATA";
    static final String OBJECT="fnd_basic_data";
    static final List<String> ORIGINAL_COLUMNS=List.of("id","dataTypeCode","basicDataId","basicDataName","basicDataAttri1",
            "sortId","createTime","createBy","updateTime","updateBy","effectiveFrom","effectiveTo");
    public static SyncDefinition create(Long connectionId){
        if(connectionId==null || connectionId<=0) throw new IllegalArgumentException("字典项来源连接无效");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).readMode("SQL")
                .sql("SELECT * FROM fnd_basic_data ORDER BY id").parameters(Map.of()).sourceKey("id").updatedAt(null)
                .columns(List.of()).filters(List.of()).mappings(ORIGINAL_COLUMNS.stream().map(name->SyncDefinition.Mapping.builder()
                        .source(name).target(name).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false)
                .maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
