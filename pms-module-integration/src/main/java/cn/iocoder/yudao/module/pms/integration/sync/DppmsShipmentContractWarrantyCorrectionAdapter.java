package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentContractWarrantyCorrectionApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsWarrantyYearsConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Adjudicated blank-to-NULL normalization before the contract year column changes type. */
@Component @RequiredArgsConstructor
public class DppmsShipmentContractWarrantyCorrectionAdapter implements DataSyncAdapter {
    public static final String ADAPTER = "DPPMS_SHIPMENT_CONTRACT_WARRANTY_CORRECTION";
    static final String PROOF = "correctionEvidence";
    static final List<String> SOURCE_COLUMNS = List.of("contract_id", "contract_code", "office_code", "contract_type",
            "customer_name", "project_name", "warranty", "marketCode", "marketName", "systemId", "systemName", "remark");
    record Proof(Long targetId, String sourceKey, String expectedFingerprint, String normalizedFingerprint,
                 String expectedSourceFieldsJson) {}
    private final ShipmentContractWarrantyCorrectionApi references;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;

    @Override public Descriptor descriptor() {
        var fields = new ArrayList<>(SOURCE_COLUMNS); fields.add(PROOF);
        return new Descriptor(ADAPTER, "已裁决发货合同空年数纠正", List.of(new ObjectDescriptor("SHIPMENT_CONTRACT",
                "原合同完整字段与纠正证据", fields.stream().map(c -> new Field(c, c, "STRING", true)).toList(),
                "COM", "ShipmentContractReference", "com_shipment_contract_reference", false)),
                List.of("RETAIN"), List.of("UPSERT"), false);
    }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public void refreshCaches() {}
    @Override public List<Change> preview(Batch batch) { return execute(batch, false); }
    @Override public List<Change> apply(Batch batch) { return execute(batch, true); }

    private List<Change> execute(Batch batch, boolean apply) {
        if (!batch.full() || !batch.adoptExisting() || batch.clearBeforeLoad() || !"RETAIN".equals(batch.missingPolicy())
                || !"UPSERT".equals(batch.loadingMode()) || batch.rows().isEmpty())
            throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_BATCH_INVALID");
        long tenant = TenantContextHolder.getRequiredTenantId();
        String[] owner = batch.owner() == null ? new String[0] : batch.owner().split(":");
        if (owner.length != 3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_OWNER_INVALID");
        var task = tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var run = task.getActiveRunId() == null ? null : runs.selectScoped(new SyncQueries.Id(tenant, task.getActiveRunId()));
        if (!ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || run == null
                || !Objects.equals(run.getTaskId(), task.getId()) || !Objects.equals(run.getConfigVersion(), task.getVersion())
                || !Objects.equals(run.getPreview(), !apply) || !(apply ? "APPLYING" : "VALIDATING").equals(run.getStatus()))
            throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_RUN_INVALID");
        var entries = new ArrayList<ShipmentContractWarrantyCorrectionApi.Entry>();
        for (var row : batch.rows()) {
            if (!"SHIPMENT_CONTRACT".equals(row.object()) || !row.fields().keySet().containsAll(SOURCE_COLUMNS)
                    || !Objects.equals(row.sourceKey(), Objects.toString(row.fields().get("contract_id"), null))
                    || !(row.fields().get(PROOF) instanceof String text))
                throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_SOURCE_INVALID");
            var proofs = JsonUtils.parseArray(text, Proof.class);
            var matches = proofs.stream().filter(p -> row.sourceKey().equals(p.sourceKey())).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_PROOF_MISSING");
            var proof = matches.getFirst();
            if (row.targetId() != null && !Objects.equals(row.targetId(), proof.targetId())
                    || proof.expectedSourceFieldsJson() == null)
                throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_IDENTITY_CHANGED");
            var expected = JsonUtils.parseMap(proof.expectedSourceFieldsJson());
            for (var column : SOURCE_COLUMNS)
                if (!expected.containsKey(column) || !Objects.equals(Objects.toString(expected.get(column), null),
                        Objects.toString(row.fields().get(column), null)))
                    throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_SOURCE_CHANGED");
            if (DppmsWarrantyYearsConverter.fromSource(row.fields().get("warranty")) != null)
                throw new IllegalArgumentException("CONTRACT_WARRANTY_CORRECTION_NOT_EMPTY");
            entries.add(new ShipmentContractWarrantyCorrectionApi.Entry(proof.targetId(), row.sourceKey(),
                    proof.expectedFingerprint(), proof.normalizedFingerprint()));
        }
        var request = new ShipmentContractWarrantyCorrectionApi.Request(tenant, batch.owner(), List.copyOf(entries));
        var results = apply ? references.apply(request) : references.preview(request);
        return results.stream().map(result -> {
            var before = new LinkedHashMap<String, Object>(); before.put("warrantyYears", result.beforeValue());
            before.put("referenceFingerprint", result.fingerprint());
            var after = new LinkedHashMap<String, Object>(); after.put("warrantyYears", null);
            after.put("referenceFingerprint", result.normalizedFingerprint());
            return new Change("SHIPMENT_CONTRACT", result.sourceKey(), result.targetId(), result.action(), before, after,
                    "DPPMS_WARRANTY_YEARS_V1: original blank has no year value; audits unchanged");
        }).toList();
    }
}
