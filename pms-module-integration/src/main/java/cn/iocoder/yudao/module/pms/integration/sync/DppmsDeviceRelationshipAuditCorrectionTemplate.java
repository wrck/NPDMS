package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDeviceRelationshipSyncTemplate.*;
/** Frozen per-key review proofs are supplied through existing LOOKUP mappings, never through business columns. */
public final class DppmsDeviceRelationshipAuditCorrectionTemplate {
    private DppmsDeviceRelationshipAuditCorrectionTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        var definition=DppmsDeviceRelationshipSyncTemplate.create(connectionId);
        var mappings=new ArrayList<>(definition.sources().getFirst().mappings());
        mappings.add(SyncDefinition.Mapping.builder().source("id").target(DppmsDeviceRelationshipAuditCorrectionAdapter.PROOF)
                .conversion("LOOKUP").values(Map.of()).build());
        mappings.add(SyncDefinition.Mapping.builder().target(DppmsDeviceRelationshipAuditCorrectionAdapter.RECEIPT)
                .conversion("CONSTANT").build());
        var source=definition.sources().getFirst().toBuilder().mappings(mappings).build();
        // Every source row is validated and retained as streaming evidence; only the UI sample is bounded.
        return definition.toBuilder().adapter(DppmsDeviceRelationshipAuditCorrectionAdapter.KEY).maxRows(500).sources(List.of(source)).build();
    }
}
