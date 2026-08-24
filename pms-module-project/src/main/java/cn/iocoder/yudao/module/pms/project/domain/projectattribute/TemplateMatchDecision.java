package cn.iocoder.yudao.module.pms.project.domain.projectattribute;

/** 一次确定性模板候选计算及其选模结论。 */
public record TemplateMatchDecision(
        String matchResult,
        String candidateDigest,
        String matcherVersion,
        String decisionMode,
        Long matchedTemplateId,
        Long matchedTemplateRevisionId,
        Integer matchedTemplateRevisionNo) {
}
