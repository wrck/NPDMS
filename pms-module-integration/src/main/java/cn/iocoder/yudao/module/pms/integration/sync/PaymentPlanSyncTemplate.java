package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.List;
import java.util.Map;

/** Legacy 09 event eligibility; node identities are explicitly configured by the operator. */
public final class PaymentPlanSyncTemplate {
    private PaymentPlanSyncTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        String sql = "SELECT pps.*, CASE WHEN (SELECT COUNT(*) FROM fnd_basic_data fbd "
                + "WHERE fbd.dataTypeCode = '09' AND fbd.basicDataName = pps.referenceEventName "
                + "AND fbd.effectiveFrom <= NOW() AND (fbd.effectiveTo > NOW() OR fbd.effectiveTo IS NULL)) = 1 "
                + "THEN pps.referenceEventName ELSE NULL END AS validReferenceEvent FROM pm_pb_plan_from_sms pps";
        var source = SyncDefinition.Source.builder().object("ACCEPTANCE").sourceObject("pm_pb_plan_from_sms")
                .readMode("SQL").sql(sql).sourceKey("id").parameters(Map.of()).columns(List.of()).filters(List.of())
                .mappings(List.of(map("contractNo", "contractNo", "TRIM"), map("referenceEvent", "validReferenceEvent", "DIRECT"),
                        map("acceptanceTime", "eventPlanHappenDate", "DATETIME"), map("nodeType", "referenceEventName", "LOOKUP"),
                        map("nodeCode", "referenceEventName", "LOOKUP"))).build();
        return EhrSyncTemplate.create(connectionId).toBuilder().adapter("PAYMENT_PLAN_ACCEPTANCE").missingPolicy("RETAIN")
                .sources(List.of(source)).build();
    }
    private static SyncDefinition.Mapping map(String target, String source, String conversion) {
        return SyncDefinition.Mapping.builder().target(target).source(source).conversion(conversion).values(Map.of()).build();
    }
}
