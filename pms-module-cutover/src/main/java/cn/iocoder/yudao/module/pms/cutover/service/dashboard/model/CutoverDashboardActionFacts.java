package cn.iocoder.yudao.module.pms.cutover.service.dashboard.model;

/** Resolved action facts for one CUT task. Contains no source document or secret. */
public record CutoverDashboardActionFacts(Long taskId, ActionFacts facts) {

    public record ActionFacts(String assessmentStatus, boolean assessmentGradePresent,
                              String checklistStatus, String planStatus, Integer planCurrentMarker,
                              String approvalStatus, String approvalNodeStatus,
                              String approvalHoldReason, Long currentApproverUserId,
                              boolean editAllowed, boolean p2SubmitAllowed,
                              boolean sourceComparable, boolean contentComplete,
                              boolean revisable, boolean approvalEligible,
                              String closureStatus, boolean closureComplete,
                              boolean failedCollectionPresent) {
        public static ActionFacts p2p3(String assessmentStatus, boolean assessmentGradePresent,
                                       String checklistStatus, boolean editAllowed, boolean p2SubmitAllowed) {
            return new ActionFacts(assessmentStatus, assessmentGradePresent, checklistStatus,
                    null, null, null, null, null, null, editAllowed, p2SubmitAllowed,
                    false, false, false, false, null, false, false);
        }

        public static ActionFacts p4(String planStatus, Integer planCurrentMarker, String approvalStatus,
                                     boolean editAllowed, boolean sourceComparable,
                                     boolean contentComplete, boolean revisable) {
            return new ActionFacts(null, false, null, planStatus, planCurrentMarker, approvalStatus,
                    null, null, null, editAllowed, false, sourceComparable, contentComplete,
                    revisable, false, null, false, false);
        }

        public static ActionFacts p5(String approvalStatus, String nodeStatus, String holdReason,
                                     Long currentApproverUserId, boolean eligible) {
            return new ActionFacts(null, false, null, null, null, approvalStatus, nodeStatus,
                    holdReason, currentApproverUserId, false, false, false, false,
                    false, eligible, null, false, false);
        }

        public static ActionFacts p6(String closureStatus, boolean editAllowed,
                                     boolean closureComplete, boolean failedCollectionPresent) {
            return new ActionFacts(null, false, null, null, null, null, null, null, null,
                    editAllowed, false, false, false, false, false, closureStatus,
                    closureComplete, failedCollectionPresent);
        }
    }

    public record PermissionFacts(boolean saveAssessment, boolean submitAssessment,
                                  boolean saveChecklist, boolean requestChecklistCollection,
                                  boolean submitChecklist, boolean createPlan, boolean savePlan,
                                  boolean submitPlan, boolean updateApprovedContacts,
                                  boolean approve, boolean reject, boolean saveClosure,
                                  boolean requestClosureCollection, boolean submitClosure) {
        public static PermissionFacts p2p3(boolean saveAssessment, boolean submitAssessment,
                                           boolean saveChecklist, boolean requestChecklistCollection,
                                           boolean submitChecklist) {
            return new PermissionFacts(saveAssessment, submitAssessment, saveChecklist,
                    requestChecklistCollection, submitChecklist, false, false, false,
                    false, false, false, false, false, false);
        }

        public static PermissionFacts p4(boolean create, boolean save, boolean submit) {
            return new PermissionFacts(false, false, false, false, false, create, save, submit,
                    save, false, false, false, false, false);
        }

        public static PermissionFacts p5() {
            return new PermissionFacts(false, false, false, false, false, false, false, false,
                    false, true, true, false, false, false);
        }

        public static PermissionFacts p6(boolean save, boolean requestCollection, boolean submit) {
            return new PermissionFacts(false, false, false, false, false, false, false, false,
                    false, false, false, save, requestCollection, submit);
        }
    }
}
