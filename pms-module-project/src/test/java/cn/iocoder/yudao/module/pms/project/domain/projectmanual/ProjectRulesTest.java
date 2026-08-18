package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-2 必填矩阵 / BR-7 可变与不可变字段表单测
 */
class ProjectRulesTest {

    // ========== BR-2 必填矩阵 ==========

    @Test
    void validManualCreationPasses() {
        List<String> missing = ProjectRules.validateManualCreation(validDraft());
        assertTrue(missing.isEmpty());
    }

    @Test
    void missingEachRequiredFieldReported() {
        // 项目名称缺失
        ProjectMasterDO draft = validDraft();
        draft.setProjectName(" ");
        List<String> missing = ProjectRules.validateManualCreation(draft);
        assertEquals(List.of("项目名称"), missing);

        // 客户编码/名称均为可选快照字段（BR-2 必填清单外），单独缺失不阻断
        draft = validDraft();
        draft.setCustomerCode(null);
        draft.setCustomerName("某客户");
        assertTrue(ProjectRules.validateManualCreation(draft).isEmpty());

        // 创建原因缺失
        draft = validDraft();
        draft.setCreationReason("");
        missing = ProjectRules.validateManualCreation(draft);
        assertEquals(List.of("创建原因"), missing);

        // 三维各自缺失
        draft = validDraft();
        draft.setSigningMethod(null);
        missing = ProjectRules.validateManualCreation(draft);
        assertEquals(List.of("签约方式"), missing);

        draft = validDraft();
        draft.setProjectCategory(null);
        missing = ProjectRules.validateManualCreation(draft);
        assertEquals(List.of("项目类别"), missing);

        draft = validDraft();
        draft.setImplementationMode(null);
        missing = ProjectRules.validateManualCreation(draft);
        assertEquals(List.of("实施方式"), missing);
    }

    @Test
    void allFieldsMissingReportedTogether() {
        List<String> missing = ProjectRules.validateManualCreation(new ProjectMasterDO());
        assertEquals(List.of("项目名称", "创建原因", "签约方式", "项目类别", "实施方式"), missing);
    }

    @Test
    void majorProjectLevelNullableMeansUnlimited() {
        // BR-2：重大项目级别可空=不限（手工场景保持空）
        ProjectMasterDO draft = validDraft();
        draft.setMajorProjectLevel(null);
        assertTrue(ProjectRules.validateManualCreation(draft).isEmpty());
        draft.setMajorProjectLevel("");
        assertTrue(ProjectRules.validateManualCreation(draft).isEmpty());
    }

    // ========== BR-7 可变/不可变字段表 ==========

    @Test
    void editableFieldsWhitelisted() {
        assertTrue(ProjectRules.isEditableField("projectName"));
        assertTrue(ProjectRules.isEditableField("customerId"));
        assertTrue(ProjectRules.isEditableField("customerCode"));
        assertTrue(ProjectRules.isEditableField("customerName"));
        assertTrue(ProjectRules.isEditableField("contractNo"));
        assertTrue(ProjectRules.isEditableField("implementationLocation"));
    }

    @Test
    void immutableFieldsRejected() {
        assertFalse(ProjectRules.isEditableField("projectCode"));
        assertFalse(ProjectRules.isEditableField("parentId"));
        assertFalse(ProjectRules.isEditableField("sourceType"));
        assertFalse(ProjectRules.isEditableField("status"));
        assertFalse(ProjectRules.isEditableField("lifecycleTemplateId"));
        assertFalse(ProjectRules.isEditableField("lifecycleTemplateRevisionNo"));
        assertFalse(ProjectRules.isEditableField("templateLoadMethod"));
        assertFalse(ProjectRules.isEditableField("signingMethod"));
        assertFalse(ProjectRules.isEditableField("creationReason"));
    }

    @Test
    void applyImmutableFieldsKeepsEditableChangesFromPayload() {
        ProjectMasterDO current = persistedProject();
        ProjectMasterDO update = new ProjectMasterDO();
        update.setId(100L);
        update.setProjectName("新名称");
        update.setContractNo("HT-2026-002");
        update.setImplementationLocation("上海");

        ProjectRules.applyImmutableFields(update, current);

        assertEquals("新名称", update.getProjectName());
        assertEquals("HT-2026-002", update.getContractNo());
        assertEquals("上海", update.getImplementationLocation());
    }

    @Test
    void applyImmutableFieldsOverwritesPayloadWithPersistedValues() {
        ProjectMasterDO current = persistedProject();
        ProjectMasterDO update = new ProjectMasterDO();
        update.setId(100L);
        // 攻击性载荷：尝试篡改编码/状态/来源/模板绑定/四维
        update.setProjectCode("PJT9999999999");
        update.setParentId(999L);
        update.setStatus(ProjectRules.STATUS_S6);
        update.setSourceType(ProjectRules.SOURCE_TYPE_ORDER);
        update.setLifecycleTemplateId(888L);
        update.setLifecycleTemplateRevisionNo(99);
        update.setTemplateLoadMethod(ProjectRules.TEMPLATE_LOAD_MANUAL_SELECTED);
        update.setSigningMethod("HACK");
        update.setProjectCategory("HACK");
        update.setImplementationMode("HACK");
        update.setCreationReason("HACK");

        ProjectRules.applyImmutableFields(update, current);

        assertEquals("PJT202600001", update.getProjectCode());
        assertNullSafe(update.getParentId());
        assertEquals(ProjectRules.STATUS_S0, update.getStatus());
        assertEquals(ProjectRules.SOURCE_TYPE_MANUAL, update.getSourceType());
        assertEquals(5L, update.getLifecycleTemplateId());
        assertEquals(2, update.getLifecycleTemplateRevisionNo());
        assertEquals(ProjectRules.TEMPLATE_LOAD_AUTO_DEFAULT, update.getTemplateLoadMethod());
        assertEquals("DIRECT_SIGN", update.getSigningMethod());
        assertEquals("GENERAL", update.getProjectCategory());
        assertEquals("DIRECT_SERVICE", update.getImplementationMode());
        assertEquals("紧急立项", update.getCreationReason());
    }

    // ========== 常量语义 ==========

    @Test
    void initialStatusIsS0() {
        assertEquals("S0", ProjectRules.INITIAL_STATUS);
        assertEquals("MAINT", ProjectRules.STATUS_MAINT);
    }

    // ========== 辅助 ==========

    private ProjectMasterDO validDraft() {
        ProjectMasterDO draft = new ProjectMasterDO();
        draft.setProjectName("某数据中心建设项目");
        draft.setCustomerCode("CUST-001");
        draft.setCustomerName("某客户");
        draft.setCreationReason("紧急立项");
        draft.setSigningMethod("DIRECT_SIGN");
        draft.setProjectCategory("GENERAL");
        draft.setImplementationMode("DIRECT_SERVICE");
        return draft;
    }

    private ProjectMasterDO persistedProject() {
        ProjectMasterDO current = validDraft();
        current.setId(100L);
        current.setProjectCode("PJT202600001");
        current.setCodeRootId(100L);
        current.setProjectSequence(0);
        current.setCodeRuleVersion("V1");
        current.setParentId(null);
        current.setStatus(ProjectRules.STATUS_S0);
        current.setSourceType(ProjectRules.SOURCE_TYPE_MANUAL);
        current.setLifecycleTemplateId(5L);
        current.setLifecycleTemplateRevisionNo(2);
        current.setTemplateLoadMethod(ProjectRules.TEMPLATE_LOAD_AUTO_DEFAULT);
        current.setContractNo("HT-2026-001");
        return current;
    }

    private void assertNullSafe(Object value) {
        assertTrue(value == null, "期望保持库内空值");
    }
}
