package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;

public final class EhrSyncTemplate {
    private EhrSyncTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        Map<String,Object> states=Map.of("NULL",0,"false",0,"true",1,"0",0,"1",1);
        var status=SyncDefinition.Mapping.builder().target("status").source("isDisabled").conversion("ENUM").values(states).build();
        // Preserve the normalization used by the approved one-time EHR import.
        var company=List.of(mapping("code","compCode","TRIM"),mapping("name","compName","TRIM"),status);
        var department=new ArrayList<>(List.of(mapping("code","depCode","TRIM"),mapping("name","depName","TRIM"),status));
        department.addAll(List.of(mapping("parentKey","adminID","REFERENCE"),mapping("companyKey","compID","REFERENCE"),
                mapping("sort","depID","LONG")));
        var companies=SyncDefinition.Source.builder().object("COMPANY").sourceObject("ehr_company").readMode("TABLE")
                .table("ehr_company").parameters(Map.of()).sourceKey("compID")
                .columns(List.of("compID","compCode","compName","isDisabled")).filters(List.of()).mappings(company).build();
        var departments=SyncDefinition.Source.builder().object("DEPARTMENT").sourceObject("ehr_department").readMode("TABLE")
                .table("ehr_department").parameters(Map.of()).sourceKey("depID")
                .columns(List.of("depID","depCode","depName","isDisabled","adminID","compID"))
                .filters(List.of()).mappings(department).build();
        return SyncDefinition.builder().adapter("EHR_ORGANIZATION").sourceSystem("DPPMS").connectionId(connectionId)
                .mode("SNAPSHOT").missingPolicy("DISABLE").cron("0 0 * * * ?").fullCron("0 0 2 * * ?")
                .overlapSeconds(300).retryCount(0).retryIntervalSeconds(60).maxRows(10000).maxBytes(64L*1024*1024)
                .sources(List.of(companies,departments)).build();
    }
    private static SyncDefinition.Mapping mapping(String target,String source,String type) {
        return SyncDefinition.Mapping.builder().target(target).source(source).conversion(type).values(Map.of()).build();
    }
}
