package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsLocalContractSourceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** The approved local Commerce source is dispatched by identity, never by a client-supplied SQL string. */
@Component @RequiredArgsConstructor
public class SyncLocalOwnerReader {
    private final DppmsLocalContractSourceApi contracts;
    private final cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi devices;
    record Input(SyncDefinition definition, SyncDefinition.Source source) {}
    static boolean reference(SyncDefinition.Source source) {return "LOCAL_OWNER".equals(source.readMode());}
    static void validate(Input input) {
        var d=input.definition();var s=input.source();
        boolean deviceBase=(DppmsDeviceBootstrapSyncTemplate.ADAPTER.equals(d.adapter()) || DppmsDirectDeviceBaseTemplate.ADAPTER.equals(d.adapter()))
                && DppmsDeviceBootstrapSyncTemplate.BASE.equals(s.object()) && "ast_device_shipment".equals(s.sourceObject())
                && "deviceSn".equals(s.sourceKey()) && (s.parameters()==null || s.parameters().isEmpty());
        if(deviceBase) {
            if(!"DPPMS".equals(d.sourceSystem()) || !"ONCE".equals(d.mode()) || !"STREAMING_CURSOR".equals(d.effectiveReadStrategy())
                    || !"RESTART_ALL".equals(d.effectiveRestartPolicy()) || s.queryRef()!=null || s.sql()!=null || s.table()!=null
                    || s.groupKey()!=null || s.rowProjection()!=null || s.filters()!=null && !s.filters().isEmpty())
                throw new IllegalArgumentException("设备基础承接只读取正式Owner的本地发货事实");
            return;
        }
        if(!"TABLE_MAPPING".equals(d.adapter()) || !"delivery-full-sync".equals(cn.iocoder.yudao.module.pms.integration.sync.GenericDirectTaskLine.businessKey(d))
                || !"DPPMS".equals(d.sourceSystem()) || !"ONCE".equals(d.mode())
                || !"STREAMING_CURSOR".equals(d.effectiveReadStrategy()) || !"RESTART_ALL".equals(d.effectiveRestartPolicy())
                || !"CONTRACT".equals(s.object()) || !"com_sales_order".equals(s.sourceObject())
                || !"migrationKey".equals(s.sourceKey()) || s.queryRef()!=null || s.sql()!=null || s.table()!=null
                || s.groupKey()!=null || s.rowProjection()!=null || s.publishedProjectMatch()!=null
                || s.filters()!=null && !s.filters().isEmpty()
                || s.parameters()!=null && !Set.of("contractNos").containsAll(s.parameters().keySet()))
            throw new IllegalArgumentException("本地Owner来源仅支持已批准的P15合同派生入口");
    }
    List<Map<String,Object>> read(Input input) {
        validate(input);var d=input.definition();var s=input.source();
        if((DppmsDeviceBootstrapSyncTemplate.ADAPTER.equals(d.adapter()) || DppmsDirectDeviceBaseTemplate.ADAPTER.equals(d.adapter()))) {
            return devices.snapshot(new cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi.SnapshotQuery(
                    TenantContextHolder.getRequiredTenantId(),d.executionActorId(),List.of(),d.maxBytes())).stream().map(seed->{
                var row=new LinkedHashMap<String,Object>(JsonUtils.parseMap(JsonUtils.toJsonString(seed)));
                DppmsDeviceBootstrapSyncTemplate.COLUMNS.forEach(name->row.putIfAbsent(name,null));
                return Collections.unmodifiableMap(row);
            }).toList();
        }
        String selected=Objects.toString(s.parameters()==null?null:s.parameters().get("contractNos"),"");
        var facts=contracts.read(new DppmsLocalContractSourceApi.Query(TenantContextHolder.getRequiredTenantId(),
                d.executionActorId(),selected.isBlank()?List.of():List.of(selected.split(",")),d.maxBytes()));
        return facts.stream().map(fact->{
            var row=new LinkedHashMap<String,Object>();
            row.put("company_code",fact.companyCode());row.put("contract_no",fact.contractNo());
            row.put("customer_code",fact.customerCode());row.put("customer_name",fact.customerName());
            row.put("migration_issue",fact.migrationIssue());row.put("master_origin","DPPMS_ERP");
            row.put("origin_table","com_sales_order");
            row.put("migrationKey",JsonUtils.toJsonString(List.of(fact.companyCode(),fact.contractNo())));
            return Collections.unmodifiableMap(row);
        }).toList();
    }
}
