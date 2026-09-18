package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanInitializationSnapshotTest {
    private final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    private final ProjectNodeExecutionMapper rounds = mock(ProjectNodeExecutionMapper.class);
    private final ProjectTemplateService templates = mock(ProjectTemplateService.class);
    private final ProjectRuleTimerScheduler timers = mock(ProjectRuleTimerScheduler.class);
    private final ProjectPlanInitializationService service = new ProjectPlanInitializationService(plans, rounds, templates, timers);
    private ProjectMasterDO project;
    private ProjectTemplateRevisionDO source;
    private TemplateExecutionSnapshot supplied;
    private TemplateExecutionSnapshot published;
    private ProjectStageExecutionContractDO stage;
    private ProjectTaskExecutionContractDO task;

    @BeforeEach
    void setUp() {
        project = new ProjectMasterDO();
        project.setId(9L); project.setTenantId(7L); project.setLifecycleTemplateId(100L);
        project.setLifecycleTemplateRevisionId(102L); project.setLifecycleTemplateRevisionNo(2);
        source = new ProjectTemplateRevisionDO();
        source.setId(102L); source.setTenantId(7L); source.setTemplateId(100L);
        source.setRevisionNo(2); source.setStatus("PUBLISHED");
        source.setDesignerDocument("{\"schemaVersion\":2}");
        when(templates.getRevisionById(102L)).thenReturn(source);
        supplied = new TemplateExecutionSnapshot(); supplied.setCompilerVersion("historical");
        var stageNode = new TemplateExecutionSnapshot.StageContract(); stageNode.setNodeKey("stage:a");
        var taskNode = new TemplateExecutionSnapshot.TaskContract(); taskNode.setNodeKey("task:a");
        supplied.setStages(List.of(stageNode)); supplied.setTasks(List.of(taskNode));
        published = JsonUtils.parseObject(JsonUtils.toJsonString(supplied), TemplateExecutionSnapshot.class);
        when(templates.getExecutionSnapshot(100L, 2)).thenReturn(published);
        stage = new ProjectStageExecutionContractDO();
        stage.setId(31L); stage.setStageId(11L); stage.setSourceNodeKey("stage:a");
        stage.setTenantId(7L); stage.setProjectId(9L);
        task = new ProjectTaskExecutionContractDO();
        task.setId(32L); task.setProjectTaskId(12L); task.setSourceNodeKey("task:a"); task.setTenantId(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MATCH", "CLOSURE", "ADMISSION", "EXIT", "TASK", "COMPILER"})
    void rejectsAnySuppliedContentThatDiffersFromTheExactPublication(String field) {
        switch (field) {
            case "MATCH" -> supplied.setMatchRuleKey("changed");
            case "CLOSURE" -> supplied.setClosureRuleKey("changed");
            case "ADMISSION" -> supplied.getStages().getFirst().setAdmissionRuleKey("changed");
            case "EXIT" -> supplied.getStages().getFirst().setExitRuleKey("changed");
            case "TASK" -> supplied.getTasks().getFirst().setDescription("changed");
            case "COMPILER" -> supplied.setCompilerVersion("changed");
            default -> throw new AssertionError(field);
        }
        rejectBeforeWrite("PUBLISHED_EXECUTION_SNAPSHOT_MISMATCH");
        verify(templates).getExecutionSnapshot(100L, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TENANT", "TEMPLATE", "REVISION_ID", "REVISION_NO", "STATUS", "DESIGNER"})
    void sourceIdentityCannotBeSubstituted(String field) {
        switch (field) {
            case "TENANT" -> source.setTenantId(8L);
            case "TEMPLATE" -> source.setTemplateId(101L);
            case "REVISION_ID" -> source.setId(103L);
            case "REVISION_NO" -> source.setRevisionNo(3);
            case "STATUS" -> source.setStatus("DRAFT");
            case "DESIGNER" -> source.setDesignerDocument(" ");
            default -> throw new AssertionError(field);
        }
        rejectBeforeWrite("PUBLISHED_DESIGNER_REQUIRED");
        verify(templates, never()).getExecutionSnapshot(anyLong(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAGE_TENANT", "STAGE_PROJECT", "TASK_TENANT"})
    void rejectsAvailableContractScopeMismatches(String field) {
        switch (field) {
            case "STAGE_TENANT" -> stage.setTenantId(8L);
            case "STAGE_PROJECT" -> stage.setProjectId(10L);
            case "TASK_TENANT" -> task.setTenantId(8L);
            default -> throw new AssertionError(field);
        }
        rejectBeforeWrite("FROZEN_CONTRACT_SCOPE_MISMATCH");
    }

    @ParameterizedTest
    @ValueSource(strings = {"STAGE_KEY", "TASK_KEY", "STAGE_ID", "TASK_ID", "CONTRACT_ID"})
    void equalCountsDoNotMakeTheWrongContractsComplete(String field) {
        switch (field) {
            case "STAGE_KEY" -> stage.setSourceNodeKey("stage:other");
            case "TASK_KEY" -> task.setSourceNodeKey("task:other");
            case "STAGE_ID" -> stage.setStageId(null);
            case "TASK_ID" -> task.setProjectTaskId(0L);
            case "CONTRACT_ID" -> task.setId(null);
            default -> throw new AssertionError(field);
        }
        rejectBeforeWrite("FROZEN_CONTRACT_SET_INCOMPLETE");
    }

    @Test
    void publicationReadFailureCannotProduceAPlanOrTimers() {
        when(templates.getExecutionSnapshot(100L, 2)).thenThrow(new IllegalArgumentException("BROKEN_PUBLICATION"));
        rejectBeforeWrite("BROKEN_PUBLICATION");
    }

    @Test
    void keepsBothNodeKindsAndSchedulesOnlyTheVerifiedPublication() {
        when(plans.insert(any(ProjectPlanVersionDO.class))).thenAnswer(call -> {
            call.getArgument(0, ProjectPlanVersionDO.class).setId(51L); return 1;
        });
        when(rounds.insert(any(ProjectNodeExecutionDO.class))).thenReturn(1);
        when(plans.attachInitialPlan(any())).thenReturn(1);
        service.initialize(project, supplied, List.of(stage), List.of(task));
        verify(rounds).insert(argThat((ProjectNodeExecutionDO row) -> "STAGE".equals(row.getNodeKind())
                && row.getNodeInstanceId().equals(11L) && row.getContractId().equals(31L)));
        verify(rounds).insert(argThat((ProjectNodeExecutionDO row) -> "TASK".equals(row.getNodeKind())
                && row.getNodeInstanceId().equals(12L) && row.getContractId().equals(32L)));
        verify(timers).schedule(eq(9L), eq(51L), same(published), isNull());
        assertEquals(51L, project.getActivePlanVersionId());
    }

    private void rejectBeforeWrite(String code) {
        String historicalDesigner = source.getDesignerDocument();
        String historicalSnapshot = JsonUtils.toJsonString(published);
        var failure = assertThrows(IllegalArgumentException.class,
                () -> service.initialize(project, supplied, List.of(stage), List.of(task)));
        assertEquals(code, failure.getMessage());
        verifyNoInteractions(plans, rounds, timers);
        assertNull(project.getActivePlanVersionId());
        assertEquals(historicalDesigner, source.getDesignerDocument());
        assertEquals(historicalSnapshot, JsonUtils.toJsonString(published));
    }
}
