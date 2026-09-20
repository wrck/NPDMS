package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectPaymentAcceptanceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class PaymentPlanAcceptanceAdapter implements DataSyncAdapter {
    private final ProjectPaymentAcceptanceApi project;
    @Override public Descriptor descriptor() {
        return new Descriptor("PAYMENT_PLAN_ACCEPTANCE", "回款节点计划验收时间", List.of(new ObjectDescriptor("ACCEPTANCE", "回款验收节点",
                List.of(new Field("contractNo", "合同号", "STRING", true), new Field("referenceEvent", "09类参考事件", "STRING", true),
                        new Field("nodeType", "目标类型（STAGE/TASK，显式映射）", "STRING", true),
                        new Field("nodeCode", "目标阶段/任务编码（显式映射）", "STRING", true),
                        new Field("acceptanceTime", "计划验收时间", "DATETIME", true)), "PROJ", "PaymentAcceptanceInput",
                "proj_payment_acceptance_binding", false)), List.of("RETAIN"), List.of("UPSERT"), false);
    }
    @Override public List<Change> preview(Batch batch) { return execute(batch, false); }
    @Override public List<Change> apply(Batch batch) { return execute(batch, true); }
    @Override public void refreshCaches() { }
    private List<Change> execute(Batch batch, boolean apply) {
        if (batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("回款验收输入只允许更新并保留来源缺失记录");
        var entries = new ArrayList<ProjectPaymentAcceptanceApi.Entry>();
        for (var row : batch.rows()) {
            if (!"ACCEPTANCE".equals(row.object())) throw new IllegalArgumentException("回款验收对象类型无效");
            var fields = row.fields();
            entries.add(new ProjectPaymentAcceptanceApi.Entry(row.sourceKey(), string(fields, "contractNo"), string(fields, "referenceEvent"),
                    string(fields, "nodeType"), string(fields, "nodeCode"), fields.get("acceptanceTime") == null ? null
                    : SyncFieldMapper.sourceTime(fields.get("acceptanceTime")), row.targetId()));
        }
        var request = new ProjectPaymentAcceptanceApi.Request(TenantContextHolder.getRequiredTenantId(), batch.owner(), entries, batch.adoptExisting());
        var result = apply ? project.refresh(request) : project.preview(request);
        var changes = new ArrayList<Change>();
        for (int i = 0; i < result.size(); i++) {
            var r = result.get(i); var after = new LinkedHashMap<>(batch.rows().get(i).fields());
            var before = new LinkedHashMap<String, Object>(); before.put("acceptanceTime", r.before() == null ? null : r.before().toString());
            changes.add(new Change("ACCEPTANCE", r.sourceKey(), r.targetId(), r.action(), before, after, r.message()));
        }
        return changes;
    }
    private static String string(Map<String, Object> fields, String key) { return Objects.toString(fields.get(key), null); }
}
