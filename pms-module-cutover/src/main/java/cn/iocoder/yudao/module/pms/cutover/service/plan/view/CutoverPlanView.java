package cn.iocoder.yudao.module.pms.cutover.service.plan.view;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record CutoverPlanView(Long taskId, Integer taskVersion, Long planRevisionId, Integer revisionNo,
                              Integer planVersion, String editMode, String status, JsonNode content,
                              List<String> allowedActions) {
}
