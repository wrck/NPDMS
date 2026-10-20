package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

import tools.jackson.databind.JsonNode;
import java.util.Map;

/** 仅补充审批原始资料的显示说明，不包含当前清单答案。 */
public record CutoverApprovalSourcePresentation(CutoverChecklistMatchingOptions matchingOptions,
                                                Map<String, JsonNode> answerSchemas,
                                                boolean incomplete) {
    public static CutoverApprovalSourcePresentation unavailable() {
        return new CutoverApprovalSourcePresentation(null, Map.of(), true);
    }
}
