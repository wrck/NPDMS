package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Approved eight missing departments, with complete EHR fields and FND precedence. */
public final class DppmsEhrDepartmentCompletionTemplate {
    private DppmsEhrDepartmentCompletionTemplate() {}
    static final String ADAPTER="DPPMS_EHR_DEPARTMENT_COMPLETION";
    static final List<String> CODES=List.of("167010","167020","167050","162007003","162012003","162022003","310409001","310422001");
    static final List<String> ORIGINAL_COLUMNS=List.of("depID","depCode","depName","depAbbr","compID","adminID","depGrade","depType","depProperty","depCost",
            "director","director2","depEmp","depNum","effectDate","xOrder","isDisabled","disabledDate","remark","depCustom1","depCustom2","depCustom3","depCustom4","depCustom5");
    static final List<String> REFERENCES=List.of("migrationParentCode","migrationDirectorLoginCount","migrationDirectorAccount",
            "migrationDirectorFndCount","migrationDirectorFndId","migrationDirectorTCount","migrationDirectorTId");
    static List<String> columns() {
        var fields=new ArrayList<>(ORIGINAL_COLUMNS);fields.addAll(DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS.stream().map(c->"migrationFnd_"+c).toList());
        fields.addAll(REFERENCES);return List.copyOf(fields);
    }
    public static SyncDefinition create(Long connectionId) {
        Map<String,Object> parameters=new LinkedHashMap<>();var placeholders=new ArrayList<String>();
        for(int i=0;i<CODES.size();i++){String name="departmentCode"+i;parameters.put(name,CODES.get(i));placeholders.add(":"+name);}
        var selected=new ArrayList<>(ORIGINAL_COLUMNS.stream().map(c->"isDisabled".equals(c)?"CAST(d.isDisabled AS UNSIGNED) AS isDisabled":"d."+c).toList());
        selected.addAll(DppmsDepartmentSyncTemplate.ORIGINAL_COLUMNS.stream().map(c->"f."+c+" AS migrationFnd_"+c).toList());
        selected.addAll(List.of("p.depCode AS migrationParentCode","l.loginCount AS migrationDirectorLoginCount","l.account AS migrationDirectorAccount",
                "fu.matchCount AS migrationDirectorFndCount","fu.id AS migrationDirectorFndId","tu.matchCount AS migrationDirectorTCount","tu.id AS migrationDirectorTId"));
        String sql="SELECT "+String.join(",",selected)+" FROM ehr_department d LEFT JOIN fnd_department f ON CAST(f.departmentNum AS BINARY)=CAST(d.depCode AS BINARY)"
                +" LEFT JOIN ehr_department p ON p.depID=d.adminID"
                +" LEFT JOIN (SELECT empID,COUNT(*) AS loginCount,MIN(account) AS account FROM ehr_login GROUP BY empID) l ON l.empID=d.director"
                +" LEFT JOIN (SELECT username,COUNT(*) AS matchCount,MIN(id) AS id FROM fnd_user_info GROUP BY username) fu ON CAST(fu.username AS BINARY)=CAST(l.account AS BINARY)"
                +" LEFT JOIN (SELECT user_name,COUNT(*) AS matchCount,MIN(user_id) AS id FROM t_user GROUP BY user_name) tu ON CAST(tu.user_name AS BINARY)=CAST(l.account AS BINARY)"
                +" WHERE d.depCode IN ("+String.join(",",placeholders)+")";
        var source=SyncDefinition.Source.builder().object("ehr_department").sourceObject("ehr_department").readMode("SQL").sql(sql).parameters(parameters)
                .sourceKey("depID").updatedAt(null).columns(List.of()).filters(List.of()).mappings(columns().stream().map(c->SyncDefinition.Mapping.builder()
                        .target(c).source(c).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN")
                .cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false)
                .maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
