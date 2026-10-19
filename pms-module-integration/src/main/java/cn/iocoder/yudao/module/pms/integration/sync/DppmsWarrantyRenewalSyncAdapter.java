package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DeviceWarrantyRenewalMigrationApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsWarrantyRenewalSyncTemplate.*;

@Component
@RequiredArgsConstructor
public class DppmsWarrantyRenewalSyncAdapter implements DataSyncAdapter {
    private final DeviceWarrantyRenewalMigrationApi renewals;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi eligibility;
    @Override public boolean supportsStreaming() { return true; }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public void refreshCaches() {}
    @Override public Descriptor descriptor() {
        return new Descriptor(ADAPTER, "DPPMS设备续保记录", List.of(new ObjectDescriptor(OBJECT, "设备续保记录",
                DeviceWarrantyRenewalMigrationApi.ORIGINAL_COLUMNS.stream().map(name -> new Field(name, name, "STRING", true)).toList(),
                "AST", "DeviceWarrantyRenewalRecord", "ast_device_warranty_renewal_record", false)),
                List.of("RETAIN"), List.of("UPSERT"), false, true);
    }
    @Override @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch, false); }
    @Override @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Change> apply(Batch batch) { return execute(batch, true); }

    private List<Change> execute(Batch batch, boolean apply) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        if (batch == null || batch.owner() == null || !batch.owner().matches("integration:" + tenant + ":[1-9][0-9]*")
                || batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy())) fail("运行范围无效");
        var task = tasks.required(SyncFieldMapper.primaryKey(batch.owner().substring(batch.owner().lastIndexOf(':') + 1)));
        var definition = JsonUtils.parseObject(task.getDefinition(), SyncDefinition.class);
        var run = task.getActiveRunId() == null ? null : runs.selectScoped(new SyncQueries.Id(tenant, task.getActiveRunId()));
        if (!ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || definition == null
                || !Set.of("ONCE", "SNAPSHOT").contains(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run == null || !Objects.equals(run.getTenantId(), tenant) || !Objects.equals(run.getTaskId(), task.getId())
                || !Objects.equals(run.getConfigVersion(), task.getVersion()) || !Objects.equals(run.getPreview(), !apply)
                || !(apply ? Set.of("READING", "APPLYING") : Set.of("READING", "VALIDATING")).contains(run.getStatus()))
            fail("配置或运行已变化；无原更新时间的续保只支持完整读取");
        var prior = new HashMap<String, Binding>();
        for (var binding : batch.bindings())
            if (!OBJECT.equals(binding.object()) || prior.put(binding.sourceKey(), binding) != null) fail("绑定身份重复");
        var keys = new HashSet<String>();
        var entries = new ArrayList<DeviceWarrantyRenewalMigrationApi.Entry>();
        var qualification=new HashMap<String,cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi.Result>();
        var serials=batch.rows().stream().map(row->text(row.fields().get("barcode"))).filter(Objects::nonNull).distinct().toList();
        for(int i=0;i<serials.size();i+=500) {
            var page=serials.subList(i,Math.min(i+500,serials.size()));
            for(var result:eligibility.resolve(new cn.iocoder.yudao.module.pms.asset.api.migration.WarrantyShipmentEligibilityApi.Query(tenant,SyncFieldMapper.primaryKey(run.getCreator()),page)))
                if(!page.contains(result.deviceSn()) || qualification.putIfAbsent(result.deviceSn(),result)!=null)fail("本地资格回执身份冲突");
        }
        if(!qualification.keySet().equals(new HashSet<>(serials)))fail("本地资格回执不完整");
        var skipped=new HashMap<String,Change>();
        for (var row : batch.rows()) {
            if (!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(DeviceWarrantyRenewalMigrationApi.ORIGINAL_COLUMNS)
                    || !keys.add(row.sourceKey())) fail("原十字段不完整或身份重复");
            var f = row.fields();
            var source = new DeviceWarrantyRenewalMigrationApi.Source(text(f.get("id")), text(f.get("con_xb")),
                    text(f.get("barcode")), text(f.get("grade")), text(f.get("begin_date")), text(f.get("end_date")),
                    text(f.get("warranty")), text(f.get("remark")), text(f.get("isyb")), text(f.get("state")));
            if (!Objects.equals(source.sourceKey(), row.sourceKey())) fail("原ID＋SN与来源键不一致");
            var binding = prior.get(row.sourceKey());
            if (binding == null && row.targetId() != null || binding != null
                    && (binding.targetId() == null || !Objects.equals(binding.targetId(), row.targetId())
                    || text(binding.lastFields().get("fingerprint")) == null)) fail("来源绑定已变化");
            var qualified=qualification.get(source.deviceSn());
            if(qualified==null || qualified.issue()!=null || !qualified.eligible()) {
                if(binding!=null || row.targetId()!=null)fail("已绑定续保的本地资格已变化，禁止覆盖");
                skipped.put(row.sourceKey(),new Change(OBJECT,row.sourceKey(),null,qualified==null||qualified.issue()!=null?"ISSUE":"SKIPPED",Map.of(),row.fields(),
                        qualified==null?"WARRANTY_LOCAL_SHIPMENT_MISSING":qualified.issue()!=null?qualified.issue():"原合同类型或RMA条件不满足"));
                continue;
            }
            entries.add(new DeviceWarrantyRenewalMigrationApi.Entry(row.sourceKey(), source, row.targetId(),
                    binding == null ? null : text(binding.lastFields().get("fingerprint"))));
        }
        var request = new DeviceWarrantyRenewalMigrationApi.Request(tenant, SyncFieldMapper.primaryKey(run.getCreator()),
                batch.owner(), List.copyOf(entries));
        var results = entries.isEmpty()?List.<DeviceWarrantyRenewalMigrationApi.Result>of():apply ? renewals.apply(request) : renewals.preview(request);
        if (results.size() != entries.size()) fail("Owner回执不完整");
        var eligibleKeys=entries.stream().map(DeviceWarrantyRenewalMigrationApi.Entry::sourceKey).collect(java.util.stream.Collectors.toSet());
        var returned = new HashSet<String>();
        var changes = new ArrayList<Change>();
        for (var result : results) {
            if (!eligibleKeys.contains(result.sourceKey()) || !returned.add(result.sourceKey())
                    || !Set.of("CREATED", "UPDATED", "UNCHANGED").contains(result.action())
                    || result.fingerprint() == null || apply && result.targetId() == null) fail("Owner回执身份无效");
            var binding = prior.get(result.sourceKey());
            changes.add(new Change(OBJECT, result.sourceKey(), result.targetId(), result.action(),
                    binding == null ? Map.of() : binding.lastFields(),
                    Map.of("renewal", result.fields(), "fingerprint", result.fingerprint()),
                    "原十字段承接正式续保记录；参考年数转月数，实际起止日期保持"));
        }
        var byKey=new HashMap<String,Change>();changes.forEach(change->byKey.put(change.sourceKey(),change));byKey.putAll(skipped);
        return batch.rows().stream().map(row->byKey.get(row.sourceKey())).toList();
    }
    private static String text(Object value) {
        return value == null || value instanceof tools.jackson.databind.JsonNode node && node.isNull() ? null : value.toString();
    }
    private static void fail(String message) { throw new IllegalArgumentException("DPPMS续保迁移：" + message); }
}
