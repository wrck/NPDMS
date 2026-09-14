package cn.iocoder.yudao.module.pms.project.domain.rule;

/** Completion and exit are condition slots, not polymorphic strategy outputs. */
public record StageCompletionEvidence(Long executionId, Long planVersionId,
                                      RuleEvaluation completion, RuleEvaluation exit,
                                      java.util.List<BusinessResult> businessResults,
                                      BusinessFactEvidence businessFacts) {
    public StageCompletionEvidence { businessResults = businessResults == null ? java.util.List.of() : java.util.List.copyOf(businessResults); }
    public record BusinessResult(Long associationId, String ownerContext, String objectType, String objectId, String factVersion) { }
}
