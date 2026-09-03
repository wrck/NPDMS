package cn.iocoder.yudao.module.pms.cutover.api.spare.dto;

import static cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareCallbackContractRules.*;

public record SpareStatusCallbackResult(
        Long applicationReferenceId,
        Long statusVersion,
        SpareStatusCallbackOutcome outcome) {

    public SpareStatusCallbackResult {
        positive(applicationReferenceId, "applicationReferenceId");
        positive(statusVersion, "statusVersion");
        if (outcome == null) throw invalid("outcome");
    }
}
