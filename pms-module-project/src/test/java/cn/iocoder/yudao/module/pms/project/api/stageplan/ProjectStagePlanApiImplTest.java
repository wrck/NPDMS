package cn.iocoder.yudao.module.pms.project.api.stageplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStagePlanApiImplTest {
    final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectTaskInstanceMapper tasks = mock(ProjectTaskInstanceMapper.class);
    final ProjectStagePlanApiImpl api = new ProjectStagePlanApiImpl(stages, projects, plans, tasks);
    ProjectMasterDO project;
    ProjectStageInstanceDO first, last;
    TemplateExecutionSnapshot snapshot;
    ProjectPlanVersionDO plan;
    final LocalDate start = LocalDate.of(2026, 1, 1), end = LocalDate.of(2026, 1, 10);

    @BeforeEach void setup() {
        project = new ProjectMasterDO(); project.setId(7L); project.setTenantId(1L);
        project.setActivePlanVersionId(9L); project.setSigningMethod("DIRECT_SIGN");
        when(projects.selectById(7L)).thenReturn(project);
        first = new ProjectStageInstanceDO(); first.setId(11L); first.setProjectId(7L); first.setTenantId(1L); first.setCode("A"); first.setVersion(1);
        last = new ProjectStageInstanceDO(); last.setId(12L); last.setProjectId(7L); last.setTenantId(1L); last.setCode("B"); last.setVersion(1);
        last.setAcceptanceTime(LocalDate.of(2026, 2, 10).atStartOfDay());
        when(stages.selectListByProjectId(7L)).thenReturn(List.of(first, last));
        when(tasks.selectListByProjectId(7L)).thenReturn(List.of());
        snapshot = new TemplateExecutionSnapshot();
        var a = new TemplateExecutionSnapshot.StageContract(); a.setCode("A"); a.setStart(true); a.setSchedulePercentage(new BigDecimal("40"));
        var b = new TemplateExecutionSnapshot.StageContract(); b.setCode("B"); b.setTerminal(true); b.setSchedulePercentage(new BigDecimal("60"));
        var edge = new TemplateExecutionSnapshot.TransitionContract(); edge.setFromStageCode("A"); edge.setToStageCode("B");
        snapshot.setStages(List.of(a,b)); snapshot.setTransitions(List.of(edge));
        plan = new ProjectPlanVersionDO(); plan.setId(9L); plan.setTenantId(1L); plan.setProjectId(7L);
        when(plans.selectById(9L)).thenReturn(plan);
        freeze();
    }
    void freeze() { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); }
    @Test void directSigningUsesPlannedAcceptanceAndFrozenPercentages() {
        var result = api.calculateSchedule(1L, 7L, start, end);
        assertEquals(LocalDate.of(2026,2,1), result.start());
        assertEquals(LocalDate.of(2026,2,10), result.end());
        assertEquals(LocalDate.of(2026,2,4), result.stages().getFirst().planEndTime());
        assertTrue(result.inputSnapshot().contains("\"acceptanceTime\":\"2026-02-10T00:00\""),
                "Stored acceptance constraints must remain ISO dates that the approval validator can read");
        verify(stages, never()).updateSchedule(any());
    }
    @Test void nonDirectUsesDurationWindow() {
        project.setSigningMethod("INDIRECT_SIGN");
        var result = api.calculateSchedule(1L,7L,start,end);
        assertEquals(start,result.start()); assertEquals(end,result.end());
    }
    @Test void noGuessedPercentageOrAcceptanceDate() {
        last.setAcceptanceTime(null);
        assertThrows(IllegalArgumentException.class, () -> api.calculateSchedule(1L,7L,start,end));
        last.setAcceptanceTime(end.atStartOfDay());
        snapshot.getStages().getFirst().setSchedulePercentage(null); freeze();
        assertThrows(IllegalArgumentException.class, () -> api.calculateSchedule(1L,7L,start,end));
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
