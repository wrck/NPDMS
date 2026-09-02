package cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain;

/** P2人工评估四项答案。 */
public record CutoverAssessmentAnswers(
        String businessImportanceLevel,
        String operationComplexityLevel,
        String hiddenRiskLevel,
        Boolean sparePartApplied) {

    public boolean complete() {
        return present(businessImportanceLevel)
                && present(operationComplexityLevel)
                && present(hiddenRiskLevel)
                && sparePartApplied != null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
