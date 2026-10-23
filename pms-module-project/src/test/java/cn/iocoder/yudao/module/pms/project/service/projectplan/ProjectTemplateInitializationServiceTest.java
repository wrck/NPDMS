package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectTemplateInitializationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ExistingProjectRuntimeInitializer;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateSelectionService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import org.junit.jupiter.api.*;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTemplateInitializationServiceTest {
    final ProjectPlanDraftService access = mock(ProjectPlanDraftService.class);
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectTemplateInitializationMapper rows = mock(ProjectTemplateInitializationMapper.class);
    final ProjectTemplateService templates = mock(ProjectTemplateService.class);
    final ProjectTemplateSelectionService selection = mock(ProjectTemplateSelectionService.class);
    final ProjectTemplateMapper templateRows = mock(ProjectTemplateMapper.class);
    final ProjectRuleFields fields = mock(ProjectRuleFields.class);
    final TaskStateMachineMapper states = mock(TaskStateMachineMapper.class);
    final ExistingProjectRuntimeInitializer runtime = mock(ExistingProjectRuntimeInitializer.class);
    final ProjectStageAdmissionService admission = mock(ProjectStageAdmissionService.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final ProjectTemplateInitializationService service = new ProjectTemplateInitializationService(access, projects,
            rows, templates, selection, templateRows, fields, states, runtime, admission, commands);
    final ProjectTemplateInitializationService.Initialize command = new ProjectTemplateInitializationService.Initialize(9L, 0L, 80L);
    ProjectMasterDO project;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(access.authorize(9L, 1L)).thenReturn(new ProjectPlanScopeQuery(7L, 9L));
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L);
        project.setLifecycleStatus("ACTIVE"); project.setAssignmentStatus("UNASSIGNED"); project.setVersion(0L);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        var revision = new ProjectTemplateRevisionDO(); revision.setId(80L); revision.setTemplateId(8L); revision.setRevisionNo(2);
        when(selection.requireAvailable(80L, 7L)).thenReturn(revision);
        var template = new ProjectTemplateDO(); template.setId(8L); template.setTenantId(7L); template.setStatus("ACTIVE");
        when(templateRows.lockTemplate(any())).thenReturn(template);
        when(fields.creationFacts(project)).thenReturn(new TemplateMatchFacts(Map.of()));
        var candidate = new TemplateMatchCandidate(); candidate.setTemplateId(8L); candidate.setTemplateRevisionId(80L);
        when(templates.matchPreview(any())).thenReturn(TemplateMatchResult.matched(candidate));
        when(commands.execute(any(),any(),eq(ProjectTemplateInitializationService.Initialized.class),any(),any())).thenAnswer(inv ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<?>)inv.getArgument(3)).get()));
    }

    @Test void forbiddenScopeCannotReadOrBind() {
        when(access.authorize(9L,1L)).thenThrow(new org.springframework.security.access.AccessDeniedException("forbidden"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.initialize(command,1L,"key"));
        verifyNoInteractions(projects, rows, runtime);
    }

    @Test void foreignTenantCannotInitialize() {
        project.setTenantId(8L); rejectBeforeBinding();
    }

    @Test void closedProjectCannotInitialize() {
        project.setLifecycleStatus("NORMAL_CLOSED"); rejectBeforeBinding();
    }

    @Test void existingPlanCannotBeOverwritten() {
        project.setActivePlanVersionId(1L); rejectBeforeBinding();
    }

    @Test void originalTemplateReferenceCannotBeOverwritten() {
        project.setLifecycleTemplateRevisionId(2L); rejectBeforeBinding();
    }

    @Test void orphanRuntimeBlocksInitialization() {
        when(rows.hasRuntime(any())).thenReturn(true); rejectBeforeBinding();
    }

    @Test void staleProjectVersionCannotBind() {
        project.setVersion(1L); rejectBeforeBinding();
    }

    @Test void noMatchingTemplateCannotBind() {
        when(templates.matchPreview(any())).thenReturn(TemplateMatchResult.noMatch("none")); rejectBeforeBinding();
    }

    @Test void olderPublicationCannotReplaceLatestMatchingRevision() {
        var candidate = new TemplateMatchCandidate(); candidate.setTemplateRevisionId(81L);
        when(templates.matchPreview(any())).thenReturn(TemplateMatchResult.matched(candidate)); rejectBeforeBinding();
    }

    @Test void retiredTemplateCannotBindAfterSelection() {
        var retired = new ProjectTemplateDO(); retired.setTenantId(7L); retired.setStatus("RETIRED");
        when(templateRows.lockTemplate(any())).thenReturn(retired); rejectBeforeBinding();
    }

    @Test void replayDoesNotRunRuntimeOrAdmissionAgain() {
        var saved = new ProjectTemplateInitializationService.Initialized(9L,51L,8L,80L);
        when(commands.execute(any(),any(),eq(ProjectTemplateInitializationService.Initialized.class),any(),any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,saved));
        assertEquals(saved,service.initialize(command,1L,"key"));
        verifyNoInteractions(rows,runtime,admission);
    }

    @Test void initializeUsesPublishedRuntimeAndPreservesProjectFacts() {
        allowInitialization();
        doAnswer(inv -> { project.setActivePlanVersionId(51L); return null; }).when(runtime).initialize(any());
        var result = service.initialize(command,1L,"key");
        assertEquals(51L,result.planVersionId());
        assertEquals(8L,result.templateId());
        assertEquals(80L,result.templateRevisionId());
        assertEquals("ACTIVE",project.getLifecycleStatus());
        assertEquals("UNASSIGNED",project.getAssignmentStatus());
        verify(admission).activateEligible(9L,1L,"PROJECT_TEMPLATE_INITIALIZE:9");
    }

    @Test void compareAndSetFailureCannotCreateRuntime() {
        allowInitialization();
        when(rows.bindIfUninitialized(any())).thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.initialize(command,1L,"key"));
        verifyNoInteractions(runtime,admission);
    }

    @Test void runtimeFailurePropagatesToCommandTransactionWithoutAdmission() {
        allowInitialization();
        doThrow(new IllegalStateException("initialization failed")).when(runtime).initialize(any());
        assertThrows(IllegalStateException.class, () -> service.initialize(command,1L,"key"));
        verifyNoInteractions(admission);
    }

    private void allowInitialization() {
        when(templates.getExecutionSnapshot(8L,2)).thenReturn(new TemplateExecutionSnapshot());
        var machine = new cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO();
        machine.setId(3L); when(states.selectCurrentPublished(any())).thenReturn(machine);
        when(rows.bindIfUninitialized(any())).thenReturn(1);
    }

    private void rejectBeforeBinding() {
        assertThrows(RuntimeException.class, () -> service.initialize(command,1L,"key"));
        verify(rows,never()).bindIfUninitialized(any());
        verifyNoInteractions(runtime,admission);
        assertEquals("UNASSIGNED",project.getAssignmentStatus());
    }
}
