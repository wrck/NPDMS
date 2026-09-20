package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Read-only source template for the regular CRM execution-order header. */
public final class DppmsExecutionOrderSyncTemplate {
    private DppmsExecutionOrderSyncTemplate() {}

    public static SyncDefinition create(Long connectionId) {
        List<SyncDefinition.Mapping> mappings = new ArrayList<>();
        for (String[] pair : FIELDS) mappings.add(SyncDefinition.Mapping.builder()
                .target(pair[0]).source(pair[1]).conversion("DIRECT").build());
        List<String> columns = new ArrayList<>(List.of("id"));
        for (String[] pair : FIELDS) columns.add(pair[1]);
        var source = SyncDefinition.Source.builder().object("EXECUTION_ORDER")
                .sourceObject("pm_project_property_from_sms").readMode("TABLE")
                .table("pm_project_property_from_sms").parameters(Map.of()).columns(columns).filters(List.of()).sourceKey("id")
                .mappings(mappings).build();
        return SyncDefinition.builder().adapter("DPPMS_CRM_EXECUTION_ORDER").sourceSystem("DPPMS")
                .connectionId(connectionId).mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN")
                .cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").overlapSeconds(0)
                .retryCount(0).retryIntervalSeconds(60).autoPaging(true).fetchSize(2000).chunkSize(1000)
                .maxRows(2000).maxBytes(64L * 1024 * 1024).sources(List.of(source)).build();
    }

    private static final String[][] FIELDS = {
            {"executionNo","orderExecNumber"},{"sourceSystem","dataSource"},{"projectCode","projectCode"},
            {"projectName","projectName"},{"salesRepCode","salesManCode"},{"salesRepName","salesManName"},
            {"marketCode","marketCode"},{"marketName","marketName"},{"systemSourceKey","systemId"},
            {"systemName","systemName"},{"expendSourceKey","expendId"},{"expendName","expendName"},
            {"industryCode","industryId"},{"industryName","industryName"},{"departmentCode","officeCode"},
            {"departmentName","officeName"},{"serviceTypeName","serviceTypeName"},{"channelName","channelName"},
            {"engineeringFeeRaw","engineeFee"},{"sourceObjectId","objId"},{"applyType","applyType"},
            {"companyCode","corporationCode"},{"customerProjectName","customerProjectName"},
            {"finalCustomerName","finalCustomerName"},{"agentName","agentName"},{"majorProjectLevel","majorProjectLevel"},
            {"projectAmount","projectMoney"},{"submitTime","submitTime"},{"predictedBidTime","predBidDate"},
            {"contactName","linkmanName"},{"contactPhone","linkmanTel"}
    };
}
