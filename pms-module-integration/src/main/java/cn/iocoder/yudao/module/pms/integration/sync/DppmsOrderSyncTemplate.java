package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Source definitions stay unpaged; STREAMING_CURSOR executes each expensive SQL once and commits bounded chunks. */
public final class DppmsOrderSyncTemplate {
    private DppmsOrderSyncTemplate() {}

    public static SyncDefinition create(Long connectionId) {
        return SyncDefinition.builder().adapter("DPPMS_ERP_ORDER").sourceSystem("DPPMS")
                .connectionId(connectionId).mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN")
                .cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").overlapSeconds(0)
                .retryCount(0).retryIntervalSeconds(60).autoPaging(false)
                .readStrategy("STREAMING_CURSOR").fetchSize(2000).chunkSize(1000)
                .restartPolicy("RESTART_ALL").queryTimeoutSeconds(0)
                .maxRows(2000).maxBytes(64L*1024*1024)
                .sources(List.of(source(false),source(true))).build();
    }

    private static SyncDefinition.Source source(boolean line) {
        String table=line?"pm_order_line_from_erp":"pm_order_data_from_erp";
        String type=line?"lineType":"orderType";
        String groupKeys="source,compCode,"+type+",orderNumber"+(line?",lineNum":"");
        String fields=line
                ? "itemCode,itemDesc,orderQuantity,openQuantity,bundleCode,warrantyMonth,profitCenter,realOrderExecNumber,customInfo"
                : "orderCreateTime,customerRequireTime,customerCode,customerName,projectName,orderComment,salesType";
        String join="r.source <=> g.source AND r.compCode <=> g.compCode AND r."+type+" <=> g."+type
                +" AND r.orderNumber <=> g.orderNumber"+(line?" AND r.lineNum <=> g.lineNum":"");
        String parent=line?",EXISTS (SELECT 1 FROM pm_order_data_from_erp h WHERE h.source=r.source AND h.compCode=r.compCode "
                +"AND h.orderType=r.lineType AND h.orderNumber=r.orderNumber GROUP BY h.source,h.compCode,h.orderType,h.orderNumber "
                +"HAVING MAX(h.syncTime) IS NOT NULL AND COUNT(DISTINCT JSON_ARRAY(h.orderCreateTime,h.customerRequireTime,h.customerCode,"
                +"h.customerName,h.projectName,h.orderComment,h.salesType))=1) AS migrationParentExists":"";
        String sql="SELECT r.*,g.migrationVersion,CASE WHEN g.payloadVariants > 1 THEN 'DUPLICATE_BUSINESS_KEY_CONFLICT' ELSE NULL END AS migrationIssue "+parent
                +" FROM "+table+" r "
                +"JOIN (SELECT "+groupKeys+",MAX(syncTime) AS migrationVersion,"
                +"COUNT(DISTINCT JSON_ARRAY("+fields+")) AS payloadVariants "
                +"FROM "+table+" GROUP BY "+groupKeys+") g ON "+join;
        List<SyncDefinition.Mapping> mappings=new ArrayList<>();
        mappings.add(map("erpSource","source"));mappings.add(map("companyCode","compCode"));
        mappings.add(map("orderNo","orderNumber"));mappings.add(map("orderType",type));
        mappings.add(map("sourceUpdatedAt","migrationVersion"));mappings.add(map("migrationIssue","migrationIssue"));
        if(line) {
            mappings.add(map("migrationParentExists","migrationParentExists"));
            for(String[] pair:List.of(new String[]{"lineNo","lineNum"},new String[]{"itemCode","itemCode"},
                    new String[]{"itemDescription","itemDesc"},new String[]{"orderQuantity","orderQuantity"},
                    new String[]{"openQuantity","openQuantity"},new String[]{"bundleCode","bundleCode"},
                    new String[]{"warrantyMonth","warrantyMonth"},new String[]{"profitCenter","profitCenter"},
                    new String[]{"realExecutionNo","realOrderExecNumber"}))mappings.add(map(pair[0],pair[1]));
        } else {
            for(String[] pair:List.of(new String[]{"customerCode","customerCode"},new String[]{"customerName","customerName"},
                    new String[]{"salesType","salesType"},new String[]{"sourceProjectName","projectName"},
                    new String[]{"orderComment","orderComment"},new String[]{"orderCreateTime","orderCreateTime"},
                    new String[]{"customerRequiredTime","customerRequireTime"}))mappings.add(map(pair[0],pair[1]));
        }
        return SyncDefinition.Source.builder().object(line?"LINE":"ORDER").sourceObject(table)
                .readMode("SQL").sql(sql).parameters(Map.of()).columns(List.of()).filters(List.of())
                .sourceKey("id").updatedAt("migrationVersion").mappings(mappings).build();
    }
    private static SyncDefinition.Mapping map(String target,String source) {
        return SyncDefinition.Mapping.builder().target(target).source(source).conversion("DIRECT").build();
    }
}
