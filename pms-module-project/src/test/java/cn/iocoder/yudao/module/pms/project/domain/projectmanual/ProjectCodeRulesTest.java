package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编码规则（ADR-0020 / BR-8）：格式、序号语义、不可变与不释放
 */
class ProjectCodeRulesTest {

    @Test
    void rootCodeFormatPrefixYearAndPaddedSequence() {
        assertEquals("PJT2026000001", ProjectCodeRules.buildRootCode(2026, 1));
        assertEquals("PJT2026000042", ProjectCodeRules.buildRootCode(2026, 42));
        assertEquals("PJT2026999999", ProjectCodeRules.buildRootCode(2026, 999_999L));
        assertEquals("PJT2000000001", ProjectCodeRules.buildRootCode(2000, 1));
    }

    @Test
    void sequenceOutOfBoundsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildRootCode(2026, 0));
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildRootCode(2026, 1_000_000L));
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildRootCode(2026, -1));
        assertTrue(ProjectCodeRules.isSequenceExhausted(0));
        assertTrue(ProjectCodeRules.isSequenceExhausted(1_000_000L));
        assertFalse(ProjectCodeRules.isSequenceExhausted(1));
        assertFalse(ProjectCodeRules.isSequenceExhausted(999_999L));
    }

    @Test
    void invalidYearRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildRootCode(999, 1));
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildRootCode(10000, 1));
    }

    @Test
    void rootSequenceSemantics() {
        // 根项目 project_sequence=0（自建命名空间）；子项目序号>0 属 PM-02
        assertTrue(ProjectCodeRules.isRootSequence(0));
        assertFalse(ProjectCodeRules.isRootSequence(1));
        assertEquals(0, ProjectCodeRules.ROOT_PROJECT_SEQUENCE);
        assertEquals("V1", ProjectCodeRules.CODE_RULE_VERSION);
        assertEquals("PJT", ProjectCodeRules.CODE_PREFIX);
    }

    @Test
    void childCodeFormatRootPrefixAndPaddedSequence() {
        // PM-02 子项目编码：<根项目编码>-SP<流水6位零填充>
        assertEquals("PJT2026000001-SP000001", ProjectCodeRules.buildChildCode("PJT2026000001", 1));
        assertEquals("PJT2026000001-SP000042", ProjectCodeRules.buildChildCode("PJT2026000001", 42));
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildChildCode("", 1));
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildChildCode(null, 1));
        assertThrows(IllegalArgumentException.class, () -> ProjectCodeRules.buildChildCode("PJT2026000001", 0));
    }

    @Test
    void codeNeverReleasedNorSequenceRecycled() {
        // BR-8：软删除/关闭/归档不释放编码；序号不回收复用（与状态、删除标记无关）
        assertFalse(ProjectCodeRules.isCodeReleasable("S0", false));
        assertFalse(ProjectCodeRules.isCodeReleasable("S6", false));
        assertFalse(ProjectCodeRules.isCodeReleasable("MAINT", false));
        assertFalse(ProjectCodeRules.isCodeReleasable("S0", true));
        assertFalse(ProjectCodeRules.isCodeReleasable(null, true));
        assertFalse(ProjectCodeRules.isSequenceRecyclable());
    }
}
