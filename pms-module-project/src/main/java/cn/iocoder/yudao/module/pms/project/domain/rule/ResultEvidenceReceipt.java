package cn.iocoder.yudao.module.pms.project.domain.rule;

/** 完成历史只保存不可变扫描的精确引用；后续扫描不得替换本次完成所用的证据。 */
public record ResultEvidenceReceipt(Long subscriptionId, String subscriptionKey, Long scanId,
                                    Long planVersionId, Long executionId, Long contractId,
                                    int subscriptionVersion, long baselineSequence, long throughSequence) {
    public ResultEvidenceReceipt {
        if (!positive(subscriptionId) || subscriptionKey == null || subscriptionKey.isBlank() || subscriptionKey.length() > 128
                || !positive(scanId) || !positive(planVersionId) || !positive(executionId) || !positive(contractId)
                || subscriptionVersion < 0 || baselineSequence < 0 || throughSequence < baselineSequence)
            throw new IllegalArgumentException("RESULT_EVIDENCE_RECEIPT_INVALID");
    }
    private static boolean positive(Long value) { return value != null && value > 0; }
}
