package cn.iocoder.yudao.module.pms.project.domain.projectschedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Demo 页面9 / Excel 3.1：建议最迟完成倒排规则解析。 */
class StageSuggestionRulesTest {

    private static final LocalDate DURATION_END = LocalDate.of(2026, 10, 23);

    private final List<StageSuggestionRules.StageFacts> ordered = List.of(
            new StageSuggestionRules.StageFacts("S1", null),
            new StageSuggestionRules.StageFacts("S4", LocalDate.of(2026, 10, 20)),
            new StageSuggestionRules.StageFacts("S5", LocalDate.of(2026, 10, 23)));

    @Test
    void adviceChainsBackwardFromDurationAndBroughtInAcceptanceTimes() {
        var rules = List.of(
                new StageSuggestionRules.RuleFacts("S4", "CHANNEL_SIGN", "DURATION_REQUIRE", null, 0, -14, true),
                new StageSuggestionRules.RuleFacts("S4", "DIRECT_SIGN", "STAGE_PLAN", "S5", 3, -14, true),
                new StageSuggestionRules.RuleFacts("S5", "DIRECT_SIGN", "PMS_IMPORTED", null, 0, 0, true),
                new StageSuggestionRules.RuleFacts("S1", null, "STAGE_PLAN", "S4", 0, -14, true));
        var channel = StageSuggestionRules.resolveAdviceEnds(ordered, rules, "CHANNEL_SIGN", DURATION_END);
        assertEquals(DURATION_END.minusDays(14), channel.get("S4"));
        assertEquals(DURATION_END.minusDays(28), channel.get("S1"));
        assertFalse(channel.containsKey("S5"));
        var direct = StageSuggestionRules.resolveAdviceEnds(ordered, rules, "DIRECT_SIGN", DURATION_END);
        assertEquals(LocalDate.of(2026, 10, 23).plusMonths(3).minusDays(14), direct.get("S4"));
        assertEquals(LocalDate.of(2026, 10, 23), direct.get("S5"));
    }

    @Test
    void exactVariantBeatsWildcardAndDisabledRowsNeverParticipate() {
        var rules = List.of(
                new StageSuggestionRules.RuleFacts("S4", null, "DURATION_REQUIRE", null, 0, -28, true),
                new StageSuggestionRules.RuleFacts("S4", "CHANNEL_SIGN", "DURATION_REQUIRE", null, 0, -14, true),
                new StageSuggestionRules.RuleFacts("S4", "DIRECT_SIGN", "DURATION_REQUIRE", null, 0, -7, true),
                new StageSuggestionRules.RuleFacts("S1", null, "DURATION_REQUIRE", null, 0, -14, false));
        var channel = StageSuggestionRules.resolveAdviceEnds(ordered, rules, "CHANNEL_SIGN", DURATION_END);
        assertEquals(DURATION_END.minusDays(14), channel.get("S4"));
        assertFalse(channel.containsKey("S1"));
        assertEquals(Map.of("S4", DURATION_END.minusDays(7)),
                StageSuggestionRules.resolveAdviceEnds(ordered, rules, "DIRECT_SIGN", DURATION_END));
    }

    @Test
    void unresolvedAnchorsAndInvalidConfigsFailLoudly() {
        var rules = List.of(
                new StageSuggestionRules.RuleFacts("S4", null, "STAGE_PLAN", "S5", 0, -14, true));
        // 参照阶段 S5 无规则行（无解析建议）→ S4 回退工期要求锚按自身偏移产出，规则涉及阶段不留空
        assertEquals(DURATION_END.minusDays(14),
                StageSuggestionRules.resolveAdviceEnds(ordered, rules, "CHANNEL_SIGN", DURATION_END).get("S4"));
        // 工期要求未登记 → 工期锚不解析
        var durationRule = List.of(new StageSuggestionRules.RuleFacts("S4", null, "DURATION_REQUIRE", null, 0, -14, true));
        assertTrue(StageSuggestionRules.resolveAdviceEnds(ordered, durationRule, "CHANNEL_SIGN", null).isEmpty());
        // 参照必须晚于自身，否则配置缺陷响亮报错
        var backward = List.of(new StageSuggestionRules.RuleFacts("S5", null, "STAGE_PLAN", "S4", 0, -14, true));
        assertThrows(IllegalArgumentException.class,
                () -> StageSuggestionRules.resolveAdviceEnds(ordered, backward, "CHANNEL_SIGN", DURATION_END));
        var unknownSource = List.of(new StageSuggestionRules.RuleFacts("S4", null, "PERCENTAGE", null, 0, -14, true));
        assertThrows(IllegalArgumentException.class,
                () -> StageSuggestionRules.resolveAdviceEnds(ordered, unknownSource, "CHANNEL_SIGN", DURATION_END));
    }

    @Test
    void missingAcceptanceFallsBackToDurationRequireAnchorDownTheChain() {
        // 无验收带入的节点按工期要求推算：PMS_IMPORTED 与断链的 STAGE_PLAN 都回退工期要求锚，
        // 偏移仍由规则行配置驱动（如全部阶段无验收时间的项目，S5→S4→S1 倒排链照常产出）
        var noAcceptance = List.of(
                new StageSuggestionRules.StageFacts("S1", null),
                new StageSuggestionRules.StageFacts("S4", null),
                new StageSuggestionRules.StageFacts("S5", null));
        var rules = List.of(
                new StageSuggestionRules.RuleFacts("S5", "DIRECT_SIGN", "PMS_IMPORTED", null, 0, 0, true),
                new StageSuggestionRules.RuleFacts("S4", "DIRECT_SIGN", "STAGE_PLAN", "S5", 3, -14, true),
                new StageSuggestionRules.RuleFacts("S1", null, "STAGE_PLAN", "S4", 0, -14, true));
        var direct = StageSuggestionRules.resolveAdviceEnds(noAcceptance, rules, "DIRECT_SIGN", DURATION_END);
        assertEquals(DURATION_END, direct.get("S5"));
        assertEquals(DURATION_END.plusMonths(3).minusDays(14), direct.get("S4"));
        assertEquals(DURATION_END.plusMonths(3).minusDays(28), direct.get("S1"));
        // 无任何可用锚点（工期要求也未登记）时仍不产出
        assertTrue(StageSuggestionRules.resolveAdviceEnds(noAcceptance, rules, "DIRECT_SIGN", null).isEmpty());
    }
}
