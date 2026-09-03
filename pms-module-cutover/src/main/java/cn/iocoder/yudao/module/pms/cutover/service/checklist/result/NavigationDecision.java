package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record NavigationDecision(String ruleKey, Long configurationRevisionId, String target) {
}
