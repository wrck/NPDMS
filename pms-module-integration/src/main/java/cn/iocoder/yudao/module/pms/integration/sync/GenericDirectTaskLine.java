package cn.iocoder.yudao.module.pms.integration.sync;

/** Separate persisted task identities reuse the reviewed generic business contracts. */
public final class GenericDirectTaskLine {
    private GenericDirectTaskLine() {}
    public static String businessKey(SyncDefinition definition) {
        if (!"TABLE_MAPPING".equals(definition.adapter())) return definition.taskKey();
        if ("CRM".equals(definition.sourceSystem()) && "P11_DIRECT".equals(definition.taskKey())) return "crm-product";
        if ("DPPMS".equals(definition.sourceSystem())) {
            if ("P14_DIRECT".equals(definition.taskKey())) return "sales-orders-full-sync";
            if ("P15_DIRECT".equals(definition.taskKey())) return "delivery-full-sync";
        }
        return definition.taskKey();
    }
}
