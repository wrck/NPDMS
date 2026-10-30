package cn.iocoder.yudao.module.pms.project.domain.rule;

/** Completion and exit are condition slots, not polymorphic strategy outputs. */
public record StageCompletionEvidence(Long executionId, Long planVersionId,
                                      RuleEvaluation completion, RuleEvaluation exit,
                                      java.util.List<BusinessResult> businessResults,
                                      BusinessFactEvidence businessFacts,
                                      cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact approval,
                                      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                                      java.util.List<ResultEvidenceReceipt> subscriptionEvidence) {
    public StageCompletionEvidence {
        businessResults = businessResults == null ? java.util.List.of() : java.util.List.copyOf(businessResults);
        subscriptionEvidence = subscriptionEvidence == null ? null : java.util.List.copyOf(subscriptionEvidence);
    }
    public StageCompletionEvidence(Long executionId, Long planVersionId, RuleEvaluation completion, RuleEvaluation exit,
            java.util.List<BusinessResult> businessResults, BusinessFactEvidence businessFacts,
            cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact approval) {
        this(executionId,planVersionId,completion,exit,businessResults,businessFacts,approval,null);
    }
    public record BusinessResult(Long associationId, String ownerContext, String objectType, String objectId, String factVersion) { }
}
