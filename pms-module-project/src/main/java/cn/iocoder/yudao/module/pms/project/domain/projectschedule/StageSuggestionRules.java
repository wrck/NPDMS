package cn.iocoder.yudao.module.pms.project.domain.projectschedule;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo 页面9 / Excel 3.1：工期建议计划时间为按签约方式维护的倒排规则
 * （建议最迟完成 = 参照时间 - 偏移），锚点为工期要求（非直签）或带入的
 * 计划验收时间（直签）。规则由配置载体维护，本类只负责解析：参照阶段
 * 必须晚于自身（倒排链无环），停用行与未覆盖阶段不产生建议。
 * 带入验收缺失或参照链断开时回退工期要求锚：无验收日期的节点按工期
 * 要求推算，规则涉及的阶段必须产出建议，偏移仍由规则行配置驱动。
 */
public final class StageSuggestionRules {

    private StageSuggestionRules() { }

    public record StageFacts(String code, LocalDate acceptanceTime) { }
    public record RuleFacts(String stageCode, String signingMethod, String sourceType,
                            String referenceStageCode, int offsetMonths, int offsetDays, boolean enabled) { }

    /** 解析路径顺序各阶段的建议最迟完成；无任何可用锚点的阶段不出现在结果中。 */
    public static Map<String, LocalDate> resolveAdviceEnds(List<StageFacts> ordered, List<RuleFacts> rules,
                                                           String signingMethod, LocalDate durationRequireEnd) {
        require(ordered != null && !ordered.isEmpty() && rules != null, "阶段路径与规则集不能为空");
        Map<String, Integer> orderIndex = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            StageFacts stage = ordered.get(index);
            require(stage != null && stage.code() != null, "阶段路径含无效阶段");
            require(orderIndex.putIfAbsent(stage.code(), index) == null, "阶段路径包含重复阶段");
        }
        Map<String, RuleFacts> exact = new HashMap<>();
        Map<String, RuleFacts> wildcard = new HashMap<>();
        for (RuleFacts rule : rules) {
            require(rule != null && rule.stageCode() != null && rule.sourceType() != null, "建议规则含无效行");
            if (!rule.enabled()) continue;
            if (rule.signingMethod() == null) {
                wildcard.putIfAbsent(rule.stageCode(), rule);
            } else if (rule.signingMethod().equals(signingMethod)) {
                exact.putIfAbsent(rule.stageCode(), rule); // 同一阶段的重复精确行属配置缺陷，取首行
            }
        }
        Map<String, LocalDate> advice = new HashMap<>();
        for (int index = ordered.size() - 1; index >= 0; index--) {
            StageFacts stage = ordered.get(index);
            RuleFacts rule = exact.containsKey(stage.code()) ? exact.get(stage.code()) : wildcard.get(stage.code());
            if (rule == null) continue; // 未覆盖阶段：无规则建议，调用方回退既有建议
            LocalDate reference = switch (rule.sourceType()) {
                // 无验收带入时按工期要求推算（工勘要求结束日期，未登记回退本版工期），偏移保持规则配置
                case "PMS_IMPORTED" -> stage.acceptanceTime() != null ? stage.acceptanceTime() : durationRequireEnd;
                case "DURATION_REQUIRE" -> durationRequireEnd;
                case "STAGE_PLAN" -> {
                    require(rule.referenceStageCode() != null && !rule.referenceStageCode().isBlank(),
                            "阶段参照规则必须填写参照阶段");
                    Integer referenceIndex = orderIndex.get(rule.referenceStageCode());
                    require(referenceIndex != null, "建议规则参照阶段不存在：" + rule.referenceStageCode());
                    require(referenceIndex > index,
                            "建议规则参照阶段必须晚于自身：" + stage.code() + " → " + rule.referenceStageCode());
                    // 参照阶段无解析建议（无规则行或无锚）时回退工期要求锚，规则涉及阶段不留空
                    yield advice.containsKey(rule.referenceStageCode())
                            ? advice.get(rule.referenceStageCode()) : durationRequireEnd;
                }
                default -> throw new IllegalArgumentException("建议规则来源类型无效：" + rule.sourceType());
            };
            if (reference != null) advice.put(stage.code(), reference.plusMonths(rule.offsetMonths()).plusDays(rule.offsetDays()));
        }
        return advice;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
