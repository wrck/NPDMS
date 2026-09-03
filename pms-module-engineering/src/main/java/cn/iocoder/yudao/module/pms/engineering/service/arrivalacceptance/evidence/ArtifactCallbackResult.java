package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.evidence;

public record ArtifactCallbackResult(
        Outcome outcome,
        Long evidenceId,
        Integer evidenceRevision,
        String syncStatus) {

    public enum Outcome {
        APPLIED,
        DUPLICATE,
        IGNORED_MISMATCH,
        IGNORED_OUT_OF_ORDER
    }
}
