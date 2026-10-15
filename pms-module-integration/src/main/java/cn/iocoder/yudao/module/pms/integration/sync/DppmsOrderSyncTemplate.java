package cn.iocoder.yudao.module.pms.integration.sync;

/** Orders use the editable generic SQL/mapping task. Historical saved adapters remain readable. */
public final class DppmsOrderSyncTemplate {
    private DppmsOrderSyncTemplate() {}

    public static SyncDefinition create(Long connectionId) {
        return DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P14", connectionId));
    }
}
