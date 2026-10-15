package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

import static cn.iocoder.yudao.module.pms.asset.api.migration.ProductCatalogMigrationApi.*;

@Component
@RequiredArgsConstructor
public class DppmsProductCatalogSyncAdapter implements DataSyncAdapter {
    private final ProductCatalogMigrationApi products;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public boolean supportsStreaming() { return true; }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public void refreshCaches() {}
    @Override public Descriptor descriptor() {
        var columns = new ArrayList<>(DppmsProductCatalogSyncTemplate.ORIGINAL_COLUMNS);
        return new Descriptor("DPPMS_PRODUCT_CATALOG", "DPPMS缺失产品补齐与编码关联",
                List.of(new ObjectDescriptor("fb_items", "原产品目录", columns.stream().map(column ->
                        new Field(column,column,"STRING",false)).toList(), "AST", "ProductOfficial", "ast_product_official_info", false)),
                List.of("RETAIN"), List.of("UPSERT"), false, true);
    }
    @Override
    @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch,false); }
    @Override public List<Change> apply(Batch batch) { return execute(batch,true); }
    private List<Change> execute(Batch batch, boolean apply) {
        if (batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("产品承接不得清空、接管或删除已有目录");
        long tenant = TenantContextHolder.getRequiredTenantId();
        String[] owner = batch.owner() == null ? new String[0] : batch.owner().split(":");
        if (owner.length != 3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("产品承接所有权无效");
        var task = tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        if (!"DPPMS".equals(task.getSourceSystem()) || !"DPPMS_PRODUCT_CATALOG".equals(task.getAdapter())
                || task.getActiveRunId() == null)
            throw new IllegalArgumentException("产品承接不能混用来源任务或无运行记录执行");
        var run = runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if (run == null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion())
                || !Objects.equals(run.getPreview(),!apply)
                || !(apply ? Set.of("READING","APPLYING") : Set.of("READING","VALIDATING")).contains(run.getStatus()))
            throw new IllegalArgumentException("产品承接运行身份或版本失效");
        // Batch threads do not have a login session. The initiating run carries the authenticated actor.
        Long actor = SyncFieldMapper.primaryKey(run.getCreator());
        Map<String,Binding> bindings = new HashMap<>();
        for (var binding : batch.bindings()) if (bindings.put(binding.object()+":"+binding.sourceKey(),binding) != null)
            throw new IllegalArgumentException("产品来源绑定重复");
        var entries = new ArrayList<Entry>();
        for (var row : batch.rows()) {
            if (!"fb_items".equals(row.object()) || !row.fields().keySet().containsAll(DppmsProductCatalogSyncTemplate.ORIGINAL_COLUMNS)
                    || !Objects.equals(row.sourceKey(),text(row,"item")) || row.fields().containsKey("_sourcePrimaryKey"))
                throw new IllegalArgumentException("原产品字段或产品编码提取键缺失/不同");
            var binding = bindings.get(row.object()+":"+row.sourceKey());
            if (binding != null && (!Objects.equals(binding.targetId(),row.targetId()) || !binding.lastFields().containsKey("product")))
                throw new IllegalArgumentException("产品绑定目标或正式属性证据缺失");
            var last = binding == null ? null : JsonUtils.getObjectMapper().convertValue(binding.lastFields().get("product"),Snapshot.class);
            entries.add(new Entry(row.sourceKey(),new Label(text(row,"item"),text(row,"describe_"),text(row,"itemname")),row.targetId(),last));
        }
        if (entries.isEmpty()) return List.of();
        var request = new Request(tenant,actor,"integration-run:"+run.getId(),entries);
        return (apply ? products.apply(request) : products.preview(request)).stream().map(result ->
                new Change("fb_items",result.sourceKey(),result.targetId(),result.action(),fields(result.before()),fields(result.after()),
                        "租户+产品编码关联正式目录并补齐缺失产品；已有CRM名称及其他正式字段保持原值，原技术ID仅用于来源追溯")).toList();
    }
    private static Map<String,Object> fields(Snapshot snapshot) { return snapshot == null ? Map.of() : Map.of("product",snapshot); }
    private static String text(Row row,String name) { return Objects.toString(row.fields().get(name),null); }
}
