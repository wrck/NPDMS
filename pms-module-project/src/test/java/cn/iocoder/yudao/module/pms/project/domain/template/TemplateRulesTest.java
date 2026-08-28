package cn.iocoder.yudao.module.pms.project.domain.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-1/BR-3/BR-5/BR-8 状态与生命周期规则单测
 */
class TemplateRulesTest {

    @Test
    void publishRequiresDraftOrActiveWithDraftRevision() {
        assertTrue(TemplateRules.canPublish(TemplateRules.STATUS_DRAFT, true));
        assertTrue(TemplateRules.canPublish(TemplateRules.STATUS_ACTIVE, true));
        // 草稿版本缺失不可发布
        assertFalse(TemplateRules.canPublish(TemplateRules.STATUS_DRAFT, false));
        // 停用模板不得再发布
        assertFalse(TemplateRules.canPublish(TemplateRules.STATUS_RETIRED, true));
        assertFalse(TemplateRules.canPublish(null, true));
    }

    @Test
    void disableOnlyAllowedForActive() {
        assertTrue(TemplateRules.canDisable(TemplateRules.STATUS_ACTIVE));
        assertFalse(TemplateRules.canDisable(TemplateRules.STATUS_DRAFT));
        assertFalse(TemplateRules.canDisable(TemplateRules.STATUS_RETIRED));
    }

    @Test
    void deleteForbiddenForSystemReservedOrPublished() {
        assertTrue(TemplateRules.canDelete(false, false));
        // BR-8 系统保留编码不得删除
        assertFalse(TemplateRules.canDelete(true, false));
        // 留痕：存在已发布版本不得物理删除
        assertFalse(TemplateRules.canDelete(false, true));
        assertFalse(TemplateRules.canDelete(true, true));
    }

    @Test
    void draftEditableOnlyBeforeRetiredAndOnlyDraftRevision() {
        assertTrue(TemplateRules.canEditDraft(TemplateRules.STATUS_DRAFT, TemplateRules.REVISION_STATUS_DRAFT));
        assertTrue(TemplateRules.canEditDraft(TemplateRules.STATUS_ACTIVE, TemplateRules.REVISION_STATUS_DRAFT));
        // 停用模板冻结
        assertFalse(TemplateRules.canEditDraft(TemplateRules.STATUS_RETIRED, TemplateRules.REVISION_STATUS_DRAFT));
        // BR-3 已发布版本只读
        assertFalse(TemplateRules.canEditDraft(TemplateRules.STATUS_ACTIVE, TemplateRules.REVISION_STATUS_PUBLISHED));
    }

    @Test
    void codeImmutableAfterCreation() {
        assertTrue(TemplateRules.isCodeUnchanged("TPL-001", "TPL-001"));
        assertFalse(TemplateRules.isCodeUnchanged("TPL-001", "TPL-002"));
        assertFalse(TemplateRules.isCodeUnchanged(null, "TPL-001"));
    }
}
