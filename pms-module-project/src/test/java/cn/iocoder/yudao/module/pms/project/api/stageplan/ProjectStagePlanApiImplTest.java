package cn.iocoder.yudao.module.pms.project.api.stageplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStagePlanApiImplTest {
    final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectTaskInstanceMapper tasks = mock(ProjectTaskInstanceMapper.class);
    final cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.StageSuggestionRuleMapper rules =
            mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectschedule.StageSuggestionRuleMapper.class);
    final ProjectStagePlanApiImpl api = new ProjectStagePlanApiImpl(stages, projects, plans, tasks, rules);
    ProjectMasterDO project;
    ProjectStageInstanceDO first, last;
    TemplateExecutionSnapshot snapshot;
    ProjectPlanVersionDO plan;
    final LocalDate start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 1, 10);

    @BeforeEach void setup() {
        project = new ProjectMasterDO(); project.setId(7L); project.setTenantId(1L);
        project.setActivePlanVersionId(9L); project.setSigningMethod("DIRECT_SIGN");
        when(projects.selectById(7L)).thenReturn(project);
        when(rules.selectActiveRules(any())).thenReturn(List.of());
        first = new ProjectStageInstanceDO(); first.setId(11L); first.setProjectId(7L); first.setTenantId(1L); first.setCode("A"); first.setVersion(1);
        last = new ProjectStageInstanceDO(); last.setId(12L); last.setProjectId(7L); last.setTenantId(1L); last.setCode("B"); last.setVersion(1);
        last.setAcceptanceTime(LocalDate.of(2026, 2, 10).atStartOfDay());
        when(stages.selectListByProjectId(7L)).thenReturn(List.of(first, last));
        when(tasks.selectListByProjectId(7L)).thenReturn(List.of());
        snapshot = new TemplateExecutionSnapshot();
        var a = new TemplateExecutionSnapshot.StageContract(); a.setCode("A"); a.setStart(true);
        var b = new TemplateExecutionSnapshot.StageContract(); b.setCode("B"); b.setTerminal(true);
        var edge = new TemplateExecutionSnapshot.TransitionContract(); edge.setFromStageCode("A"); edge.setToStageCode("B");
        snapshot.setStages(List.of(a,b)); snapshot.setTransitions(List.of(edge));
        plan = new ProjectPlanVersionDO(); plan.setId(9L); plan.setTenantId(1L); plan.setProjectId(7L);
        when(plans.selectById(9L)).thenReturn(plan);
        freeze();
    }
    void freeze() { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); }
    @Test void windowIsTheBaselineAndDatesComeFromStageSuggestions() {
        // Demo 3.1：推算窗口就是工期基线；阶段建议日期来自阶段配置与规则，
        // 未覆盖规则的阶段留给用户填写
        first.setSuggestedStartTime(LocalDate.of(2026, 1, 1).atStartOfDay());
        first.setSuggestedEndTime(LocalDate.of(2026, 1, 4).atStartOfDay());
        var result = api.calculateSchedule(1L, 7L, start, end);
        assertEquals(start, result.start());
        assertEquals(end, result.end());
        assertEquals(LocalDate.of(2026, 1, 4), result.stages().getFirst().planEndTime());
        assertNull(result.stages().getFirst().planStartTime().isBefore(start) ? result.start() : null);
        assertNull(result.stages().get(1).planStartTime());
        assertNull(result.stages().get(1).planEndTime());
        assertTrue(result.inputSnapshot().contains("\"acceptanceTime\":\"2026-02-10T00:00\""),
                "Stored acceptance constraints must remain ISO dates that the approval validator can read");
        verify(stages, never()).updateSchedule(any());
    }
    @Test void acceptanceViolationIsCheckedAgainstTheStageSuggestion() {
        // 合同验收时间为行内约束：阶段建议结束不得晚于计划验收时间；无建议时交给提交后校验
        last.setSuggestedEndTime(LocalDate.of(2026, 2, 11).atStartOfDay());
        var violation = assertThrows(IllegalArgumentException.class, () -> api.calculateSchedule(1L,7L,start,end));
        assertTrue(violation.getMessage().contains("计划结束晚于计划验收时间"));
        last.setAcceptanceTime(null);
        assertDoesNotThrow(() -> api.calculateSchedule(1L,7L,start,end));
    }
    @Test void signingMethodNoLongerChangesTheWindow() {
        project.setSigningMethod("INDIRECT_SIGN");
        var result = api.calculateSchedule(1L,7L,start,end);
        assertEquals(start,result.start()); assertEquals(end,result.end());
    }
    @Test void adviceEndComesFromMaintainedRulesWithInstanceFallback() {
        // Demo 页面9 / Excel 3.1：建议最迟完成 = 工期要求-偏移 或 带入计划验收时间（V355 规则配置）；
        // 验收日不直接充当结束，而是与规则建议比对（建议晚于计划验收时间报错）；
        // 未覆盖规则的阶段回退阶段实例既有建议
        first.setSuggestedEndTime(LocalDate.of(2026, 1, 8).atStartOfDay());
        project.setProjectEndDate(LocalDate.of(2026, 1, 10));
        when(rules.selectActiveRules(any())).thenReturn(List.of(
                rule("A", "DIRECT_SIGN", "DURATION_REQUIRE", null, 0, -14),
                rule("B", null, "PMS_IMPORTED", null, 0, 0)));
        var result = api.calculateSchedule(1L,7L,start,end);
        assertEquals(LocalDate.of(2025, 12, 27), result.stages().getFirst().planEndTime());
        assertEquals(LocalDate.of(2026, 2, 10), result.stages().get(1).planEndTime());
        when(rules.selectActiveRules(any())).thenReturn(List.of(rule("B", null, "DURATION_REQUIRE", null, 0, 45)));
        var violation = assertThrows(IllegalArgumentException.class, () -> api.calculateSchedule(1L,7L,start,end));
        assertTrue(violation.getMessage().contains("计划结束晚于计划验收时间"));
    }
    @Test void durationRequireAnchorFallsBackToPlanDurationWhenSurveyDateAbsent() {
        // 工勘要求结束日期（Demo 工期要求）未登记时，工期要求锚点回退计划域本版工期（倒排截止）
        project.setProjectEndDate(null);
        when(rules.selectActiveRules(any())).thenReturn(List.of(rule("A", "DIRECT_SIGN", "DURATION_REQUIRE", null, 0, -14)));
        var result = api.calculateSchedule(1L,7L,start,end);
        assertEquals(LocalDate.of(2025, 12, 27), result.stages().getFirst().planEndTime());
    }
    private cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule.StageSuggestionRuleDO rule(
            String stageCode, String variant, String sourceType, String reference, int months, int days) {
        var rule = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectschedule.StageSuggestionRuleDO();
        rule.setStageCode(stageCode); rule.setSigningMethod(variant); rule.setSourceType(sourceType);
        rule.setReferenceStageCode(reference); rule.setOffsetMonths(months); rule.setOffsetDays(days);
        rule.setEnabled(true);
        return rule;
    }
    @Test void unresolvedConditionalPathCannotBeSilentlySelected() {
        snapshot.getTransitions().getFirst().setConditionRuleKey("UNRESOLVED"); freeze();
        assertThrows(IllegalArgumentException.class, () -> api.calculateSchedule(1L,7L,start,end));
    }
    @Test void parallelBranchesAllEnterThePlanInPriorityOrder() {
        // 模板允许并行分支（如"计划与方案并行、部署要求二者均完成"）：
        // 推算必须覆盖全部可达阶段并按 priority 升序决定顺序，不得只走优先级最小的一条；多分支汇聚回同一阶段合法
        var evaluator = mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator.class);
        var graphMapper = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(api, "ruleEvaluator", evaluator);
        org.springframework.test.util.ReflectionTestUtils.setField(api, "runtimeGraphMapper", graphMapper);
        when(graphMapper.selectGates(any())).thenReturn(List.of());
        var stageA = new TemplateExecutionSnapshot.StageContract(); stageA.setCode("A"); stageA.setStart(true);
        var stageB = new TemplateExecutionSnapshot.StageContract(); stageB.setCode("B");
        var stageC = new TemplateExecutionSnapshot.StageContract(); stageC.setCode("C");
        var stageD = new TemplateExecutionSnapshot.StageContract(); stageD.setCode("D"); stageD.setTerminal(true);
        var aToPlan = new TemplateExecutionSnapshot.TransitionContract();
        aToPlan.setFromStageCode("A"); aToPlan.setToStageCode("C");
        aToPlan.setCode("A_C"); aToPlan.setPriority(2); aToPlan.setDefaultBranch(false);
        var aToB = snapshot.getTransitions().getFirst();
        aToB.setCode("A_B"); aToB.setPriority(1); aToB.setDefaultBranch(false);
        var planToD = new TemplateExecutionSnapshot.TransitionContract();
        planToD.setFromStageCode("C"); planToD.setToStageCode("D");
        planToD.setCode("C_D"); planToD.setPriority(0); planToD.setDefaultBranch(false);
        var bToD = new TemplateExecutionSnapshot.TransitionContract();
        bToD.setFromStageCode("B"); bToD.setToStageCode("D");
        bToD.setCode("B_D"); bToD.setPriority(0); bToD.setDefaultBranch(false);
        snapshot.setStages(List.of(stageA, stageB, stageC, stageD));
        snapshot.setTransitions(List.of(aToB, aToPlan, bToD, planToD));
        var instanceC = new ProjectStageInstanceDO(); instanceC.setId(13L); instanceC.setProjectId(7L); instanceC.setTenantId(1L); instanceC.setCode("C"); instanceC.setVersion(1);
        var instanceD = new ProjectStageInstanceDO(); instanceD.setId(14L); instanceD.setProjectId(7L); instanceD.setTenantId(1L); instanceD.setCode("D"); instanceD.setVersion(1);
        when(stages.selectListByProjectId(7L)).thenReturn(List.of(first, last, instanceC, instanceD));
        freeze();
        var result = api.calculateSchedule(1L,7L,start,end);
        assertEquals(List.of(11L, 12L, 13L, 14L),
                result.stages().stream().map(cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi.StagePlanDate::stageId).toList(),
                "并行分支（B 计划、C 方案）必须同时参与计划且汇聚阶段 D 只出现一次");
    }
    @Test void cyclicFrozenPathIsStillRejected() {
        // 汇聚合法，但真正的环仍须拒绝推算
        snapshot.getStages().stream().filter(stage -> "B".equals(stage.getCode())).findFirst().orElseThrow().setTerminal(false);
        var aToB = snapshot.getTransitions().getFirst();
        aToB.setCode("A_B"); aToB.setPriority(1); aToB.setDefaultBranch(false);
        var bToA = new TemplateExecutionSnapshot.TransitionContract();
        bToA.setFromStageCode("B"); bToA.setToStageCode("A");
        bToA.setCode("B_A"); bToA.setPriority(1); bToA.setDefaultBranch(false);
        snapshot.setTransitions(List.of(aToB, bToA));
        freeze();
        var violation = assertThrows(IllegalArgumentException.class, () -> api.calculateSchedule(1L,7L,start,end));
        assertTrue(violation.getMessage().contains("循环"));
    }
    @Test void resolvedFrozenConditionCanSelectTheUniquePath() {
        var evaluator = mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator.class);
        var graphMapper = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(api, "ruleEvaluator", evaluator);
        org.springframework.test.util.ReflectionTestUtils.setField(api, "runtimeGraphMapper", graphMapper);
        when(graphMapper.selectGates(any())).thenReturn(List.of());
        var edge = snapshot.getTransitions().getFirst();
        edge.setCode("A_B"); edge.setPriority(0); edge.setDefaultBranch(false);
        edge.setConditionRule(JsonUtils.parseObject("{\"kind\":\"FIELD\"}", tools.jackson.databind.JsonNode.class));
        when(evaluator.evaluate(any(tools.jackson.databind.JsonNode.class), any())).thenReturn(
                cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus.SATISFIED);
        freeze();
        var result = api.calculateSchedule(1L,7L,start,end);
        assertEquals(2, result.stages().size());
        assertTrue(result.inputSnapshot().contains("SATISFIED"));
        when(evaluator.evaluate(any(tools.jackson.databind.JsonNode.class), any())).thenReturn(
                cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus.UNAVAILABLE);
        assertThrows(IllegalArgumentException.class, () -> api.calculateSchedule(1L,7L,start,end));
    }
    @Test void crossProjectStageWriteRejectedBeforeMutation() {
        assertThrows(IllegalArgumentException.class, () -> api.applyPlanDates(1L,7L,List.of(new ProjectStagePlanApi.StagePlanDate(999L,start,end))));
        verify(stages, never()).updateSchedule(any());
    }
    @Test void foreignTaskCannotBeWritten() {
        assertThrows(IllegalArgumentException.class, () -> api.applyTaskPlanDates(1L,7L,List.of(new ProjectStagePlanApi.TaskPlan(999L,null,"A","task",1,start,end,null))));
        verify(tasks, never()).updateSchedule(any());
    }
}
