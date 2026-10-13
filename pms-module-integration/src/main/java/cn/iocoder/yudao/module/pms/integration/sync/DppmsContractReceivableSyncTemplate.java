package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi;
import java.util.*;

public final class DppmsContractReceivableSyncTemplate {
    private DppmsContractReceivableSyncTemplate() {}
    static final String ADAPTER="DPPMS_CONTRACT_RECEIVABLE", OBJECT="sms_ofst_contract_head_sap";
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("回款来源连接无效");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).sourceKey("id")
                .updatedAt("update_time").readMode("SQL").sql(DppmsMigrationSql.initial("contract-receivables"))
                .parameters(Map.of("contractNos","")).columns(List.of()).filters(List.of())
                .mappings(ContractReceivableMigrationApi.ORIGINAL_COLUMNS.stream().map(name->SyncDefinition.Mapping.builder()
                        .source(name).target(name).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?")
                .fullCron("0 0 2 ? * SUN").overlapSeconds(60).retryCount(0).retryIntervalSeconds(60)
                .readStrategy("KEYSET_PAGING").fetchSize(1000).chunkSize(500).maxRows(500).maxBytes(8L*1024*1024)
                .sources(List.of(source)).build();
    }
}
