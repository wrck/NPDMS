package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_REPORT_STATE_INVALID;

/** Approved project report policy. PROJ separately evaluates the frozen template conditions. */
public final class IndependentAcceptancePolicy {
    private IndependentAcceptancePolicy() { }
    public static final String LEGACY_SNAPSHOT = """
            {"schemaVersion":1,"policyCode":"EFFECTIVE_REPORT_PASS","passingConclusion":"PASS",
             "requiredFields":["acceptanceTime","conclusionCode","acceptorName"],"minimumAttachments":1}
            """;
    public static final String UPLOAD_SNAPSHOT = """
            {"schemaVersion":2,"policyCode":"EFFECTIVE_REPORT_UPLOAD",
             "requiredFields":["acceptanceTime","uploaderUserId"],"minimumAttachments":1}
            """;
    public static final String SNAPSHOT = """
            {"schemaVersion":3,"policyCode":"EFFECTIVE_REPORT_UPLOAD_PASS","passingConclusion":"PASS",
             "requiredFields":["acceptanceTime","uploaderUserId","conclusionCode","acceptorName"],"minimumAttachments":1}
            """;

    public static boolean direct(AcceptanceActivityDO activity) { return "DIRECT".equals(activity.getOriginKind()); }
    public static boolean validSnapshot(String snapshot) {
        return uploadOnly(snapshot) || snapshot != null && JsonUtils.parseTree(LEGACY_SNAPSHOT).equals(JsonUtils.parseTree(snapshot));
    }
    public static boolean uploadOnly(String snapshot) {
        return uploadPass(snapshot) || snapshot != null && JsonUtils.parseTree(UPLOAD_SNAPSHOT).equals(JsonUtils.parseTree(snapshot));
    }
    public static boolean uploadPass(String snapshot) {
        return snapshot != null && JsonUtils.parseTree(SNAPSHOT).equals(JsonUtils.parseTree(snapshot));
    }
    public static void requireMutable(AcceptanceActivityDO activity) {
        if (!direct(activity) || activity.getProjectTaskId() != null || activity.getExecutionContractId() != null
                || activity.getDeliverableId() != null || activity.getOriginKey() == null
                || !validSnapshot(activity.getRuleSnapshot())
                || !Set.of("PENDING", "COMPLETED").contains(String.valueOf(activity.getActivityStatus())))
            throw exception(ACC_REPORT_STATE_INVALID);
    }
    public static void reportPublished(AcceptanceActivityDO activity, String conclusion, String reportSnapshot) {
        requireMutable(activity);
        if (!validSnapshot(reportSnapshot)) throw exception(ACC_REPORT_STATE_INVALID);
        activity.setActivityStatus(uploadOnly(reportSnapshot) || "PASS".equals(conclusion) ? "COMPLETED" : "PENDING");
    }
    public static void reportRevoked(AcceptanceActivityDO activity) {
        requireMutable(activity);
        activity.setActivityStatus("PENDING");
    }
}
