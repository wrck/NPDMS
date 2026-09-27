package cn.iocoder.yudao.module.pms.platform.api.businessmodel.result;

import java.util.List;

/**
 * 结果选择策略：沿用既有五类语义，字段修改时间不能替代结果形成依据；
 * 轮次边界以结果形成序号表达（baseline 不含、through 含）。
 */
public record ResultSelectionPolicy(
        Acquisition acquisition,
        Selection selection,
        Validity validity,
        String pinnedResultId,
        List<Long> expectedObjectIds) {

    public enum Acquisition {
        /** 只承认订阅基线之后形成的结果；无形成序号或早于基线一律不合格。 */
        NEW_RESULT,
        /** 允许补扫采纳既有有效结果。 */
        REUSE_EXISTING,
        /** 只承认钉住的结果身份；查不到不回退最新。 */
        PINNED_RESULT
    }

    public enum Selection { EXACT_ONE, ANY_MATCHING, ALL_EXPECTED }

    public enum Validity { ANY, CURRENT_VALID }

    public ResultSelectionPolicy {
        expectedObjectIds = expectedObjectIds == null ? List.of() : List.copyOf(expectedObjectIds);
    }

    public static ResultSelectionPolicy of(Acquisition acquisition, Selection selection) {
        return new ResultSelectionPolicy(acquisition, selection, Validity.ANY, null, List.of());
    }
}
