package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericSyncTemplates;

/** Keeps the reviewed CRM view, native AccountId and field/duplicate rules unchanged. */
public final class CrmDirectCustomerTemplate {
    public static final String ADAPTER="CRM_CUSTOMER_DIRECT";
    private CrmDirectCustomerTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("CRM来源连接无效");
        var original=new GenericSyncTemplates().list(connectionId).stream().filter(t->"crm-customer".equals(t.key())).findFirst().orElseThrow().definition();
        var sources=original.sources().stream().map(source->source.toBuilder()
                .sql(source.sql().replace("PARTITION BY AccountNumber","PARTITION BY LTRIM(RTRIM(AccountNumber))")).build()).toList();
        return original.toBuilder().adapter(ADAPTER).taskKey("P10_DIRECT").mode("ONCE").retryCount(0).sources(sources).build();
    }
}
