package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 项目树真值 / 无环 / 权重归一化 / 进度汇总规则单测（BR-1/BR-3/BR-4）
 */
class ProjectTreeRulesTest {

    // ========== 树真值维护 ==========

    @Test
    void childPathFromRootParent() {
        // 根父（path 空）→ parentId + '/'
        assertEquals("1/", ProjectTreeRules.buildChildPath("", 1L));
        assertEquals("1/", ProjectTreeRules.buildChildPath(null, 1L));
        // 非根父 → 父path + 父id + '/'
        assertEquals("1/2/", ProjectTreeRules.buildChildPath("1/", 2L));
    }

    @Test
    void parseAncestorIdsAndDescendantPrefix() {
        assertEquals(List.of(), ProjectTreeRules.parseAncestorIds(""));
        assertEquals(List.of(1L, 2L), ProjectTreeRules.parseAncestorIds("1/2/"));
        assertEquals("1/2/", ProjectTreeRules.descendantPrefix("1/", 2L));
        assertEquals("1/", ProjectTreeRules.descendantPrefix("", 1L));
    }

    @Test
    void childDepthIncrementsParent() {
        assertEquals(1, ProjectTreeRules.buildChildDepth(0));
        assertEquals(3, ProjectTreeRules.buildChildDepth(2));
    }

    // ========== 无环校验（BR-1） ==========

    @Test
    void cycleDetectionSelfAndDescendantRejected() {
        // 自身挂接
        assertTrue(ProjectTreeRules.wouldCreateCycle(5L, "1/", 5L));
        // newParent 是 node 的后代（newParentPath 含 /nodeId/）
        assertTrue(ProjectTreeRules.wouldCreateCycle(2L, "1/2/3/", 3L));
        // 合法：newParent 不是 node 的后代
        assertFalse(ProjectTreeRules.wouldCreateCycle(5L, "1/2/", 4L));
        // newParent 为空（根）不循环
        assertFalse(ProjectTreeRules.wouldCreateCycle(5L, null, null));
    }

    // ========== 权重归一化（BR-3） ==========

    @Test
    void equalWeightWhenAllNull() {
        List<BigDecimal> weights = ProjectTreeRules.normalizedWeights(
                Arrays.asList(null, null, null));
        assertEquals(3, weights.size());
        // 等权 1/3
        assertEquals(0, weights.get(0).compareTo(new BigDecimal("0.333333")));
        assertEquals(0, weights.get(1).compareTo(new BigDecimal("0.333333")));
        assertEquals(0, weights.get(2).compareTo(new BigDecimal("0.333333")));
    }

    @Test
    void manualWeightsNormalizedWhenSumIs100() {
        List<BigDecimal> weights = ProjectTreeRules.normalizedWeights(
                Arrays.asList(new BigDecimal("30"), new BigDecimal("70")));
        assertEquals(0, weights.get(0).compareTo(new BigDecimal("0.3")));
        assertEquals(0, weights.get(1).compareTo(new BigDecimal("0.7")));
    }

    @Test
    void mixedWeightsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ProjectTreeRules.normalizedWeights(
                        Arrays.asList(null, new BigDecimal("100"))));
    }

    @Test
    void weightSumNot100Rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ProjectTreeRules.normalizedWeights(
                        Arrays.asList(new BigDecimal("30"), new BigDecimal("60"))));
    }

    @Test
    void emptyWeightsReturnsEmpty() {
        assertTrue(ProjectTreeRules.normalizedWeights(List.of()).isEmpty());
    }

    // ========== 进度汇总（BR-3） ==========

    @Test
    void aggregateProgressEqualWeight() {
        List<BigDecimal> weights = ProjectTreeRules.normalizedWeights(
                Arrays.asList(null, null));
        BigDecimal aggregate = ProjectTreeRules.aggregateProgress(
                Arrays.asList(new BigDecimal("50"), new BigDecimal("100")), weights);
        assertEquals(0, aggregate.compareTo(new BigDecimal("75.00")));
    }

    @Test
    void aggregateProgressWeighted() {
        List<BigDecimal> weights = ProjectTreeRules.normalizedWeights(
                Arrays.asList(new BigDecimal("25"), new BigDecimal("75")));
        BigDecimal aggregate = ProjectTreeRules.aggregateProgress(
                Arrays.asList(new BigDecimal("40"), new BigDecimal("80")), weights);
        // 40*0.25 + 80*0.75 = 10 + 60 = 70
        assertEquals(0, aggregate.compareTo(new BigDecimal("70.00")));
    }

    @Test
    void aggregateProgressTreatsNullProgressAsZero() {
        List<BigDecimal> weights = ProjectTreeRules.normalizedWeights(
                Arrays.asList((BigDecimal) null));
        BigDecimal aggregate = ProjectTreeRules.aggregateProgress(
                Arrays.asList((BigDecimal) null), weights);
        assertEquals(0, aggregate.compareTo(BigDecimal.ZERO));
    }
}
