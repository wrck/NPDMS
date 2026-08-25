package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BR-4 实例化规则单测：五要素快照完整性、最小阶段 ACTIVE、初始态、validation_summary 拼接与截断、引用行复制
 */
class TemplateInstantiatorTest {

    @Test
    void freezesStableIdsStateRevisionAndThreeLevelClosure() {
        TemplateDefinitionContent content = fullContent();
        TemplateDefinitionContent.TaskDef grandchild = new TemplateDefinitionContent.TaskDef();
        grandchild.setTaskCode("T1-1-1");
        grandchild.setName("细化计划");
        grandchild.setParentTaskCode("T1-1");
        grandchild.setStageCode("S1");
        content.getTasks().add(grandchild);
        AtomicLong ids = new AtomicLong(1000L);

        ProjectInstantiation instantiation = TemplateInstantiator.instantiate(
                content, 100L, 88L, ids::incrementAndGet);

        ProjectTaskInstanceDO root = findByTaskCode(instantiation.getTasks(), "T1");
        ProjectTaskInstanceDO child = findByTaskCode(instantiation.getTasks(), "T1-1");
        ProjectTaskInstanceDO leaf = findByTaskCode(instantiation.getTasks(), "T1-1-1");
        assertEquals(root.getId(), root.getRootTaskId());
        assertNull(root.getParentTaskId());
        assertEquals(0, root.getTreeDepth());
        assertEquals(root.getId(), child.getParentTaskId());
        assertEquals(root.getId(), child.getRootTaskId());
        assertEquals(1, child.getTreeDepth());
        assertEquals(child.getId(), leaf.getParentTaskId());
        assertEquals(root.getId(), leaf.getRootTaskId());
        assertEquals(2, leaf.getTreeDepth());
        instantiation.getTasks().forEach(task -> assertEquals(88L, task.getStateMachineRevisionId()));
        assertEquals(6, instantiation.getTaskTreePaths().size());
        assertTrue(instantiation.getTaskTreePaths().stream().anyMatch(path ->
                root.getId().equals(path.getAncestorTaskId())
                        && leaf.getId().equals(path.getDescendantTaskId())
                        && path.getDistance() == 2));
    }

    @Test
    void fiveElementSnapshotsFullyCopied() {
        TemplateDefinitionContent content = fullContent();
        ProjectInstantiation instantiation = instantiate(content);

        // 阶段快照
        ProjectStageInstanceDO stage = findByCode(instantiation.getStages(), "S1");
        assertEquals(100L, stage.getProjectId());
        assertEquals("工前准备", stage.getName());
        assertEquals(1, stage.getSortOrder());
        assertEquals("准入说明", stage.getEntryCriteria());
        assertEquals("准出说明", stage.getExitCriteria());
        // 任务快照
        ProjectTaskInstanceDO task = findByTaskCode(instantiation.getTasks(), "T1-1");
        assertEquals("T1", task.getParentTaskCode());
        assertEquals("S1", task.getStageCode());
        assertEquals(1, task.getPriority());
        assertEquals(5, task.getSortOrder());
        assertEquals(new BigDecimal("8.0"), task.getEstimatedHours());
        assertEquals("DELIVERY", task.getSatisfactionTiming());
        assertEquals("任务说明", task.getDescription());
        // 里程碑快照
        ProjectMilestoneInstanceDO milestone = findMilestoneByCode(instantiation.getMilestones(), "M1");
        assertEquals("开工评审", milestone.getName());
        assertEquals("S1", milestone.getStageCode());
        assertEquals("S1 末", milestone.getTiming());
        assertEquals("评审通过", milestone.getCriteria());
        // 交付件由ACC Owner接口初始化，不进入PROJ实例载体的创建输出。
        assertTrue(instantiation.getDeliverables().isEmpty());
        // 门禁快照
        ProjectGateInstanceDO gate = findGateByCode(instantiation.getGates(), "G-EXIT-S1");
        assertEquals("S1 准出门禁", gate.getName());
        assertEquals("EXIT", gate.getGateType());
        assertEquals("S1", gate.getStageCode());
        assertEquals("准出说明", gate.getDescription());
    }

    @Test
    void minSortOrderStageActiveOthersPending() {
        TemplateDefinitionContent content = fullContent();
        // S0 sort=0 最小 → ACTIVE；S1/S2 PENDING
        ProjectInstantiation instantiation = instantiate(content);
        assertEquals(ProjectRules.STAGE_STATUS_ACTIVE, findByCode(instantiation.getStages(), "S0").getStatus());
        assertEquals(ProjectRules.STAGE_STATUS_PENDING, findByCode(instantiation.getStages(), "S1").getStatus());
        assertEquals(ProjectRules.STAGE_STATUS_PENDING, findByCode(instantiation.getStages(), "S2").getStatus());
    }

    @Test
    void rejectsTemplateWithoutS0() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.getStages().add(stage("S2", "实施方案", 1));
        content.getStages().add(stage("S3", "实施部署", 2));
        assertThrows(IllegalArgumentException.class, () -> instantiate(content));
    }

    @Test
    void nullSortOrderTreatedAsZero() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.getStages().add(stage("S0", "待开始", null));
        content.getStages().add(stage("S1", "工前准备", 1));
        ProjectInstantiation instantiation = instantiate(content);
        assertEquals(ProjectRules.STAGE_STATUS_ACTIVE, findByCode(instantiation.getStages(), "S0").getStatus());
    }

    @Test
    void initialStatusesForTaskMilestoneDeliverableGate() {
        ProjectInstantiation instantiation = instantiate(fullContent());
        instantiation.getTasks().forEach(task ->
                assertEquals(ProjectRules.TASK_STATUS_PENDING_ASSIGN, task.getStatus()));
        instantiation.getMilestones().forEach(milestone ->
                assertEquals(ProjectRules.MILESTONE_STATUS_PENDING, milestone.getStatus()));
        instantiation.getDeliverables().forEach(deliverable ->
                assertEquals(ProjectRules.DELIVERABLE_STATUS_PENDING, deliverable.getStatus()));
        instantiation.getGates().forEach(gate ->
                assertEquals(ProjectRules.GATE_STATUS_PENDING, gate.getStatus()));
    }

    @Test
    void validationSummaryJoinsTypeAndCodeWithSemicolon() {
        ProjectInstantiation instantiation = instantiate(fullContent());
        ProjectGateInstanceDO gate = findGateByCode(instantiation.getGates(), "G-EXIT-S1");
        assertEquals("TASK:T1;DELIVERABLE:D1", gate.getValidationSummary());
    }

    @Test
    void validationSummaryTruncatedTo1000Chars() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.getStages().add(stage("S0", "立项与指派", 0));
        TemplateDefinitionContent.GateDef gate = gate("G-BIG", "大引用门禁", "EXIT", "S0");
        List<TemplateDefinitionContent.GateRef> refs = new ArrayList<>();
        // 每条引用 "TASK:CODE-i;" 约 12 字符，100 条即超出 1000
        IntStream.rangeClosed(1, 100).forEach(i -> refs.add(ref("TASK", "CODE-000" + i, null)));
        gate.setReferences(refs);
        content.getGates().add(gate);

        ProjectInstantiation instantiation = instantiate(content);
        String summary = findGateByCode(instantiation.getGates(), "G-BIG").getValidationSummary();
        assertEquals(1000, summary.length());
    }

    @Test
    void gateWithoutReferencesHasNullSummary() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.getStages().add(stage("S0", "立项与指派", 0));
        content.getGates().add(gate("G-EMPTY", "无引用门禁", "ENTRY", "S0"));
        ProjectInstantiation instantiation = instantiate(content);
        assertNull(findGateByCode(instantiation.getGates(), "G-EMPTY").getValidationSummary());
    }

    @Test
    void gateReferencesCopiedWithVersionAndGroupedByGateCode() {
        ProjectInstantiation instantiation = instantiate(fullContent());
        Map<String, List<ProjectGateReferenceInstanceDO>> grouped = instantiation.getGateReferencesByGateCode();

        assertEquals(2, grouped.size());
        List<ProjectGateReferenceInstanceDO> exitRefs = grouped.get("G-EXIT-S1");
        assertEquals(2, exitRefs.size());
        assertEquals("TASK", exitRefs.get(0).getRefType());
        assertEquals("T1", exitRefs.get(0).getRefCode());
        assertEquals("DELIVERABLE", exitRefs.get(1).getRefType());
        assertEquals("D1", exitRefs.get(1).getRefCode());
        // PROCESS 引用带版本冻结
        List<ProjectGateReferenceInstanceDO> entryRefs = grouped.get("G-ENTRY-S1");
        assertEquals("PROCESS", entryRefs.get(0).getRefType());
        assertEquals("PROC-KEY", entryRefs.get(0).getRefCode());
        assertEquals("V3", entryRefs.get(0).getRefVersion());
        // gate_id 为落库回填槽位
        instantiation.getGateReferences().forEach(reference -> assertNull(reference.getGateId()));
        // 拍平视图一致
        assertEquals(3, instantiation.getGateReferences().size());
    }

    @Test
    void sourceDefinitionIdSlotsStayNull() {
        // F-PM03 内容模型无定义行 ID：source_definition_id 保持 NULL 映射槽
        ProjectInstantiation instantiation = instantiate(fullContent());
        instantiation.getStages().forEach(stage -> assertNull(stage.getSourceDefinitionId()));
        instantiation.getTasks().forEach(task -> assertNull(task.getSourceDefinitionId()));
        instantiation.getMilestones().forEach(milestone -> assertNull(milestone.getSourceDefinitionId()));
        instantiation.getDeliverables().forEach(deliverable -> assertNull(deliverable.getSourceDefinitionId()));
        instantiation.getGates().forEach(gate -> assertNull(gate.getSourceDefinitionId()));
    }

    @Test
    void emptyContentProducesEmptyInstantiation() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.getStages().add(stage("S0", "立项与指派", 0));
        ProjectInstantiation instantiation = instantiate(content);
        assertEquals(1, instantiation.getStages().size());
        assertEquals(ProjectRules.STAGE_STATUS_ACTIVE, instantiation.getStages().getFirst().getStatus());
        assertTrue(instantiation.getTasks().isEmpty());
        assertTrue(instantiation.getMilestones().isEmpty());
        assertTrue(instantiation.getDeliverables().isEmpty());
        assertTrue(instantiation.getGates().isEmpty());
        assertTrue(instantiation.getGateReferences().isEmpty());
    }

    // ========== 辅助 ==========

    private ProjectInstantiation instantiate(TemplateDefinitionContent content) {
        AtomicLong ids = new AtomicLong(1000L);
        return TemplateInstantiator.instantiate(content, 100L, 88L, ids::incrementAndGet);
    }

    private TemplateDefinitionContent fullContent() {
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.setProcessDefinitionKey("PROC-KEY");
        content.setProcessDefinitionVersion("V3");
        content.getStages().add(stage("S0", "立项与指派", 0));
        content.getStages().add(stage("S1", "工前准备", 1));
        content.getStages().add(stage("S2", "施工计划", 2));

        TemplateDefinitionContent.TaskDef rootTask = new TemplateDefinitionContent.TaskDef();
        rootTask.setTaskCode("T1");
        rootTask.setName("工前任务");
        rootTask.setStageCode("S1");
        rootTask.setSortOrder(1);
        content.getTasks().add(rootTask);
        TemplateDefinitionContent.TaskDef childTask = new TemplateDefinitionContent.TaskDef();
        childTask.setTaskCode("T1-1");
        childTask.setName("制定计划");
        childTask.setParentTaskCode("T1");
        childTask.setStageCode("S1");
        childTask.setPriority(1);
        childTask.setSortOrder(5);
        childTask.setEstimatedHours(new BigDecimal("8.0"));
        childTask.setSatisfactionTiming("DELIVERY");
        childTask.setDescription("任务说明");
        content.getTasks().add(childTask);

        TemplateDefinitionContent.MilestoneDef milestone = new TemplateDefinitionContent.MilestoneDef();
        milestone.setMilestoneCode("M1");
        milestone.setName("开工评审");
        milestone.setStageCode("S1");
        milestone.setTiming("S1 末");
        milestone.setCriteria("评审通过");
        content.getMilestones().add(milestone);

        TemplateDefinitionContent.DeliverableDef required = new TemplateDefinitionContent.DeliverableDef();
        required.setDeliverableCode("D1");
        required.setName("实施计划书");
        required.setStageCode("S1");
        required.setTaskCode("T1-1");
        required.setRequired(Boolean.TRUE);
        content.getDeliverables().add(required);
        TemplateDefinitionContent.DeliverableDef optional = new TemplateDefinitionContent.DeliverableDef();
        optional.setDeliverableCode("D2");
        optional.setName("会议纪要");
        optional.setStageCode("S1");
        optional.setRequired(Boolean.FALSE);
        content.getDeliverables().add(optional);

        TemplateDefinitionContent.GateDef entry = gate("G-ENTRY-S1", "S1 准入门禁", "ENTRY", "S1");
        entry.getReferences().add(ref("PROCESS", "PROC-KEY", "V3"));
        content.getGates().add(entry);
        TemplateDefinitionContent.GateDef exit = gate("G-EXIT-S1", "S1 准出门禁", "EXIT", "S1");
        exit.getReferences().add(ref("TASK", "T1", null));
        exit.getReferences().add(ref("DELIVERABLE", "D1", null));
        content.getGates().add(exit);
        return content;
    }

    private TemplateDefinitionContent.StageDef stage(String code, String name, Integer sortOrder) {
        TemplateDefinitionContent.StageDef stage = new TemplateDefinitionContent.StageDef();
        stage.setStageCode(code);
        stage.setName(name);
        stage.setSortOrder(sortOrder);
        stage.setEntryCriteria("准入说明");
        stage.setExitCriteria("准出说明");
        return stage;
    }

    private TemplateDefinitionContent.GateDef gate(String code, String name, String type, String stageCode) {
        TemplateDefinitionContent.GateDef gate = new TemplateDefinitionContent.GateDef();
        gate.setGateCode(code);
        gate.setName(name);
        gate.setGateType(type);
        gate.setStageCode(stageCode);
        gate.setDescription("准出说明");
        return gate;
    }

    private TemplateDefinitionContent.GateRef ref(String type, String code, String version) {
        TemplateDefinitionContent.GateRef ref = new TemplateDefinitionContent.GateRef();
        ref.setRefType(type);
        ref.setRefCode(code);
        ref.setRefVersion(version);
        return ref;
    }

    private <T> T findByCode(List<T> list, java.util.function.Function<T, String> codeGetter, String code) {
        return list.stream().filter(item -> code.equals(codeGetter.apply(item))).findFirst().orElseThrow();
    }

    private ProjectStageInstanceDO findByCode(List<ProjectStageInstanceDO> list, String code) {
        return findByCode(list, ProjectStageInstanceDO::getStageCode, code);
    }

    private ProjectMilestoneInstanceDO findMilestoneByCode(List<ProjectMilestoneInstanceDO> list, String code) {
        return findByCode(list, ProjectMilestoneInstanceDO::getMilestoneCode, code);
    }

    private ProjectGateInstanceDO findGateByCode(List<ProjectGateInstanceDO> list, String code) {
        return findByCode(list, ProjectGateInstanceDO::getGateCode, code);
    }

    private ProjectTaskInstanceDO findByTaskCode(List<ProjectTaskInstanceDO> list, String code) {
        return findByCode(list, ProjectTaskInstanceDO::getTaskCode, code);
    }

}
