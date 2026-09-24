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
        // Demo 3.1：推算窗口就是工期基线；阶段建议日期来自阶段配置，未配置为 null 留给用户填写
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
        // 未解析或未覆盖的阶段回退阶段实例既有建议；行内约束作用于最终建议
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
