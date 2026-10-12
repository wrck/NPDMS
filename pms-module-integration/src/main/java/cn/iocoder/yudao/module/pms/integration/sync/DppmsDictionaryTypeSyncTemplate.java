package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Approved 31 original definitions plus missing item groups; no invented Chinese labels. */
public final class DppmsDictionaryTypeSyncTemplate {
    private DppmsDictionaryTypeSyncTemplate() {}
    static final String ADAPTER="DPPMS_DICTIONARY_TYPES";
    static final String OBJECT="fnd_basic_data_type";
    static final List<String> ORIGINAL_COLUMNS=List.of("id","dataTypeCode","dataTypeName","status","createTime","createBy",
            "updateTime","updateBy","effectiveFrom","effectiveTo");
    static final String MISSING="definitionMissing";
    public static SyncDefinition create(Long connectionId){
        if(connectionId==null || connectionId<=0) throw new IllegalArgumentException("字典类型来源连接无效");
        String sql="SELECT t.id,t.dataTypeCode,t.dataTypeName,t.status,t.createTime,t.createBy,t.updateTime,t.updateBy,"
                +"t.effectiveFrom,t.effectiveTo,0 AS definitionMissing FROM fnd_basic_data_type t UNION ALL "
                +"SELECT NULL,g.dataTypeCode,g.dataTypeCode,NULL,NULL,NULL,NULL,NULL,NULL,NULL,1 AS definitionMissing "
                +"FROM (SELECT DISTINCT dataTypeCode FROM fnd_basic_data) g LEFT JOIN fnd_basic_data_type t "
                +"ON t.dataTypeCode=g.dataTypeCode WHERE t.id IS NULL";
        var columns=new ArrayList<>(ORIGINAL_COLUMNS);columns.add(MISSING);
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).readMode("SQL").sql(sql)
                .parameters(Map.of()).sourceKey("dataTypeCode").updatedAt(null).columns(List.of()).filters(List.of())
                .mappings(columns.stream().map(name->SyncDefinition.Mapping.builder().source(name).target(name).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false)
                .maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
