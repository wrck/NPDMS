package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** All original fields are read; the selected code list bounds this adjudicated directory run. */
public final class DppmsDepartmentSyncTemplate {
    private DppmsDepartmentSyncTemplate() {}
    static final List<String> ORIGINAL_COLUMNS=List.of("id","departmentNum","departmentName","isparam","status","createTime","effectiveFrom","effectiveTo","updateTime");
    static final List<String> REFERENCES=List.of("migrationEhrDepartmentId","migrationEhrDisabled");
    static final List<String> APPROVED_MISSING_CODES=List.of("160300","160700002","162028","162029","163120","163140","163220","163230","163240","163320",
            "165030","165040","165050","167060","280700","310500001","310500001001","310500001002","310500001003","310500001004","310500001005",
            "310500001006","310500001007","310500001008","310500002","310500002001","310500002002","999999");
    public static SyncDefinition create(Long connectionId){return create(connectionId,APPROVED_MISSING_CODES);}
    /** The same extraction/Owner path can be reused with a wider explicitly approved source code list. */
    public static SyncDefinition create(Long connectionId,List<String> departmentCodes) {
        if(departmentCodes==null || departmentCodes.isEmpty() || departmentCodes.size()>10000
                || departmentCodes.stream().anyMatch(code->code==null || code.isBlank() || code.length()>64)
                || new HashSet<>(departmentCodes).size()!=departmentCodes.size())throw new IllegalArgumentException("目录来源编码选择为空、重复或超出容量");
        Map<String,Object> parameters=new LinkedHashMap<>();List<String> placeholders=new ArrayList<>();
        for(int index=0;index<departmentCodes.size();index++){String name="departmentCode"+index;parameters.put(name,departmentCodes.get(index));placeholders.add(":"+name);}
        String sql="SELECT "+String.join(",",ORIGINAL_COLUMNS.stream().map(column->"d."+column).toList())
                +",e.depID AS migrationEhrDepartmentId,CAST(e.isDisabled AS UNSIGNED) AS migrationEhrDisabled"
                +" FROM fnd_department d LEFT JOIN ehr_department e ON CAST(e.depCode AS BINARY)=CAST(d.departmentNum AS BINARY)"
                +" WHERE d.departmentNum IN ("+String.join(",",placeholders)+")";
        List<String> columns=new ArrayList<>(ORIGINAL_COLUMNS);columns.addAll(REFERENCES);
        var source=SyncDefinition.Source.builder().object("fnd_department").sourceObject("fnd_department").readMode("SQL").sql(sql)
                .parameters(parameters).sourceKey("id").updatedAt(null).columns(List.of()).filters(List.of())
                .mappings(columns.stream().map(column->SyncDefinition.Mapping.builder().target(column).source(column).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter("DPPMS_DEPARTMENTS").sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").overlapSeconds(0)
                .retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
