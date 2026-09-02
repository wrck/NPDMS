package cn.iocoder.yudao.module.pms.cutover.api.spare.dto;

import static cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareCallbackContractRules.*;

public record SpareExternalReferenceBindingResult(
        Long applicationReferenceId,
        String externalApplicationNo,
        String integrationStatus,
        SpareReferenceBindingOutcome outcome) {

    public SpareExternalReferenceBindingResult {
        positive(applicationReferenceId, "applicationReferenceId");
        text(externalApplicationNo, 128, "externalApplicationNo");
        if (!"EXTERNAL_REFERENCED".equals(integrationStatus)) throw invalid("integrationStatus");
        if (outcome == null) throw invalid("outcome");
    }
}
