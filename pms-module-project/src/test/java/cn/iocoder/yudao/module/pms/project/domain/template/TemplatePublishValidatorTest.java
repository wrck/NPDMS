package cn.iocoder.yudao.module.pms.project.domain.template;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-2 发布校验规则单测：内容不完整或引用不存在不得发布
 */
class TemplatePublishValidatorTest {

    @Test
    void validContentPasses() {
        TemplateDefinitionContent content = buildValidContent();
        List<String> failures = TemplatePublishValidator.validate(content);
        assertTrue(failures.isEmpty(), () -> "应通过校验，实际失败项：" + failures);
    }

    @Test
    void nullContentRejected() {
        assertTrue(!TemplatePublishValidator.validate(null).isEmpty());
    }

    @Test
    void missingProcessReferenceRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.setProcessDefinitionKey(null);
        assertHasFailure(content, "流程定义引用缺失");
    }

    @Test
    void emptyStagesRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getStages().clear();
        assertHasFailure(content, "阶段定义缺失");
    }

    @Test
    void stageCodeOutOfLifecycleRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getStages().get(1).setStageCode("S9");
        assertHasFailure(content, "S0～S6");
    }

    @Test
    void duplicateStageCodeRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getStages().get(1).setStageCode("S0");
        assertHasFailure(content, "重复");
    }

    @Test
    void taskReferencingUnknownStageRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getTasks().get(0).setStageCode("S5");
        assertHasFailure(content, "引用的阶段【S5】不存在");
    }

    @Test
    void taskSelfParentRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getTasks().get(0).setParentTaskCode(content.getTasks().get(0).getTaskCode());
        assertHasFailure(content, "不能以自身为父任务");
    }

    @Test
    void taskUnknownParentRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getTasks().get(0).setParentTaskCode("T-NOPE");
        assertHasFailure(content, "父任务【T-NOPE】不存在");
    }

    @Test
    void milestoneUnknownStageRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getMilestones().get(0).setStageCode("S5");
        assertHasFailure(content, "里程碑【M-001】引用的阶段【S5】不存在");
    }

    @Test
    void deliverableUnknownTaskRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getDeliverables().get(0).setTaskCode("T-NOPE");
        assertHasFailure(content, "引用的任务【T-NOPE】不存在");
    }

    @Test
    void gateInvalidTypeRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getGates().get(0).setGateType("MIDDLE");
        assertHasFailure(content, "ENTRY 或 EXIT");
    }

    @Test
    void gateWithoutReferencesRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getGates().get(0).getReferences().clear();
        assertHasFailure(content, "缺少引用行");
    }

    @Test
    void gateReferencingUnknownTaskRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getGates().get(0).getReferences().get(0).setRefCode("T-NOPE");
        assertHasFailure(content, "引用的任务【T-NOPE】不存在");
    }

    @Test
    void gateReferencingUnknownDeliverableRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getGates().get(0).getReferences().get(1).setRefCode("D-NOPE");
        assertHasFailure(content, "引用的交付件【D-NOPE】不存在");
    }

    @Test
    void gateProcessReferenceWithoutVersionRejected() {
        TemplateDefinitionContent content = buildValidContent();
        content.getGates().get(0).getReferences().get(3).setRefVersion(null);
        assertHasFailure(content, "缺少版本");
    }

    @Test
    void unknownReferenceTypeRejected() {
        TemplateDefinitionContent content = buildValidContent();
        TemplateDefinitionContent.GateRef ref = new TemplateDefinitionContent.GateRef();
        ref.setRefType("UNKNOWN");
        ref.setRefCode("X");
        content.getGates().get(0).getReferences().add(ref);
        assertHasFailure(content, "未知类型的引用行");
    }

    private void assertHasFailure(TemplateDefinitionContent content, String keyword) {
        List<String> failures = TemplatePublishValidator.validate(content);
        assertTrue(failures.stream().anyMatch(f -> f.contains(keyword)),
                () -> "应包含失败项【" + keyword + "】，实际：" + failures);
    }

    /**
     * 构造完整可发布的模板内容：两阶段、两任务（父子）、里程碑、交付件、带四类引用的门禁
     */
    static TemplateDefinitionContent buildValidContent() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.setSigningMethod("DIRECT_SIGN");
        content.setProjectCategory("GENERAL");
        content.setProcessDefinitionKey("pms-standard-delivery");
        content.setProcessDefinitionVersion("1");

        TemplateDefinitionContent.StageDef s0 = new TemplateDefinitionContent.StageDef();
        s0.setStageCode("S0"); s0.setName("启动"); s0.setSortOrder(0);
        s0.setEntryCriteria("合同生效"); s0.setExitCriteria("启动会完成");
        TemplateDefinitionContent.StageDef s1 = new TemplateDefinitionContent.StageDef();
        s1.setStageCode("S1"); s1.setName("实施"); s1.setSortOrder(1);
        content.setStages(new ArrayList<>(List.of(s0, s1)));

        TemplateDefinitionContent.TaskDef t1 = new TemplateDefinitionContent.TaskDef();
        t1.setTaskCode("T-001"); t1.setName("编制项目计划"); t1.setStageCode("S0");
        t1.setPriority(1); t1.setSortOrder(0); t1.setEstimatedHours(new BigDecimal("8.0"));
        TemplateDefinitionContent.TaskDef t2 = new TemplateDefinitionContent.TaskDef();
        t2.setTaskCode("T-002"); t2.setName("环境准备"); t2.setParentTaskCode("T-001");
        t2.setStageCode("S1"); t2.setPriority(2); t2.setSortOrder(1);
        content.setTasks(List.of(t1, t2));

        TemplateDefinitionContent.MilestoneDef m1 = new TemplateDefinitionContent.MilestoneDef();
        m1.setMilestoneCode("M-001"); m1.setName("启动会"); m1.setStageCode("S0");
        m1.setTiming("S0 准出前"); m1.setCriteria("启动会纪要归档");
        content.setMilestones(List.of(m1));

        TemplateDefinitionContent.DeliverableDef d1 = new TemplateDefinitionContent.DeliverableDef();
        d1.setDeliverableCode("D-001"); d1.setName("项目计划书"); d1.setStageCode("S0");
        d1.setTaskCode("T-001"); d1.setRequired(Boolean.TRUE);
        content.setDeliverables(List.of(d1));

        TemplateDefinitionContent.GateDef g1 = new TemplateDefinitionContent.GateDef();
        g1.setGateCode("G-001"); g1.setName("S0 准出"); g1.setGateType(TemplateDefinitionContent.GATE_TYPE_EXIT);
        g1.setStageCode("S0");
        TemplateDefinitionContent.GateRef taskRef = new TemplateDefinitionContent.GateRef();
        taskRef.setRefType(TemplateDefinitionContent.REF_TYPE_TASK); taskRef.setRefCode("T-001");
        TemplateDefinitionContent.GateRef deliverableRef = new TemplateDefinitionContent.GateRef();
        deliverableRef.setRefType(TemplateDefinitionContent.REF_TYPE_DELIVERABLE); deliverableRef.setRefCode("D-001");
        TemplateDefinitionContent.GateRef stateRef = new TemplateDefinitionContent.GateRef();
        stateRef.setRefType(TemplateDefinitionContent.REF_TYPE_STATE); stateRef.setRefCode("TASK_COMPLETED");
        TemplateDefinitionContent.GateRef processRef = new TemplateDefinitionContent.GateRef();
        processRef.setRefType(TemplateDefinitionContent.REF_TYPE_PROCESS);
        processRef.setRefCode("pms-approval"); processRef.setRefVersion("1");
        g1.setReferences(new ArrayList<>(List.of(taskRef, deliverableRef, stateRef, processRef)));
        content.setGates(List.of(g1));
        return content;
    }
}
