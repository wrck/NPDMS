package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchFacts;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateMatchRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanTemplateBindingServiceTest {
    final ProjectPlanDraftService drafts = mock(ProjectPlanDraftService.class);
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
    final ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
    final ProjectTemplateMatchRuleEvaluator matches = mock(ProjectTemplateMatchRuleEvaluator.class);
    final ProjectRuleFields fields = mock(ProjectRuleFields.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final ProjectPlanTemplateBindingService service = new ProjectPlanTemplateBindingService(
            drafts, projects, plans, templates, revisions, matches, fields, commands);
    final ProjectPlanTemplateBindingService.Import command = new ProjectPlanTemplateBindingService.Import(9L, 52L, 0L, 51L, 80L);
    ProjectMasterDO project;
    ProjectTemplateRevisionDO revision;
    ProjectTemplateDO template;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(drafts.authorize(9L, 1L)).thenReturn(new ProjectPlanScopeQuery(7L, 9L));
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L);
        project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(51L); project.setProjectCategory("GENERAL");
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        var effective = new ProjectPlanVersionDO(); effective.setId(51L);
        var draft = new ProjectPlanVersionDO(); draft.setId(52L); draft.setVersion(0L); draft.setBasePlanVersionId(51L); draft.setSourceTemplateRevisionId(70L);
        when(plans.selectEffective(any())).thenReturn(effective); when(plans.selectDraft(any())).thenReturn(draft);
        revision = new ProjectTemplateRevisionDO(); revision.setId(80L); revision.setTemplateId(8L); revision.setTenantId(7L);
        revision.setStatus("PUBLISHED"); revision.setDesignerSchemaVersion(2); revision.setDesignerDocument("{\"schemaVersion\":2}");
        revision.setExecutionSnapshot("{\"executionSchemaVersion\":2}");
        when(revisions.selectById(80L)).thenReturn(revision);
        template = new ProjectTemplateDO(); template.setId(8L); template.setTenantId(7L); template.setStatus("ACTIVE");
        when(templates.lockTemplate(any())).thenReturn(template);
        when(fields.creationFacts(project)).thenReturn(new TemplateMatchFacts(Map.of()));
        when(matches.evaluate(any(),any(),any(),any())).thenReturn(evaluation(RuleEvaluation.Outcome.MATCHED));
        when(plans.bindTemplateDraftIfCurrent(any())).thenReturn(1);
        when(commands.execute(any(),anyString(),eq(ProjectPlanTemplateBindingService.Imported.class),any(),any())).thenAnswer(call ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<ProjectPlanTemplateBindingService.Imported>) call.getArgument(3)).get()));
    }
    private RuleEvaluation evaluation(RuleEvaluation.Outcome outcome) {
        return new RuleEvaluation("published:80", outcome, null, List.of(), List.of(), List.of());
    }
    private void rejected() {
        assertThrows(RuntimeException.class, () -> service.importPublished(command, 1L, "bind"));
        verify(plans, never()).bindTemplateDraftIfCurrent(any());
    }
    @Test void importsPublishedContentAndIdentityWithoutActivatingOrChangingCategory() {
        var result = service.importPublished(command, 1L, "bind");
        assertEquals(70L, result.previousTemplateRevisionId()); assertEquals(80L, result.sourceTemplateRevisionId());
        assertEquals(1L, result.draftVersion());
        verify(plans).bindTemplateDraftIfCurrent(argThat(q -> q.sourceTemplateRevisionId().equals(80L)
                && q.designer().equals(revision.getDesignerDocument()) && q.expectedVersion().equals(0L)));
        verify(plans, never()).attachActivatedPlan(any());
        assertEquals(51L, project.getActivePlanVersionId()); assertEquals("GENERAL", project.getProjectCategory());
    }
    @Test void rejectsCrossTenantRevision() { revision.setTenantId(99L); rejected(); }
    @Test void rejectsUnpublishedRevision() { revision.setStatus("DRAFT"); rejected(); }
    @Test void rejectsDisabledTemplate() { template.setStatus("DISABLED"); rejected(); }
    @Test void rejectsClosedProject() { project.setLifecycleStatus("NORMAL_CLOSED"); rejected(); }
    @Test void rejectsStaleBase() { project.setActivePlanVersionId(99L); rejected(); }
    @Test void rejectsMissingPermission() { when(drafts.authorize(9L,1L)).thenThrow(new IllegalStateException("denied")); rejected(); }
    @Test void rejectsUnknownMatchRatherThanFallingBackToDimensions() {
        when(matches.evaluate(any(),any(),any(),any())).thenReturn(evaluation(RuleEvaluation.Outcome.UNKNOWN)); rejected();
    }
    @Test void rejectsNonMatchingPublishedRule() {
        when(matches.evaluate(any(),any(),any(),any())).thenReturn(evaluation(RuleEvaluation.Outcome.NOT_MATCHED)); rejected();
    }
    @Test void rejectsConcurrentDraftWrite() {
        when(plans.bindTemplateDraftIfCurrent(any())).thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.importPublished(command,1L,"bind"));
    }

    @Test void reopenUsesAuthorizedProjectAndRequestedTemplateWithoutFixedProjectOrTemplateMapping() {
        revision.setExecutionSnapshot("{\"executionSchemaVersion\":2,\"stages\":[{\"start\":true,\"lifecycleStage\":\"S0\"}]}");
        when(revisions.selectPublishedListByTemplateId(8L)).thenReturn(List.of(revision));
        assertSame(revision, service.requireLatestForReopen(new ProjectPlanTemplateBindingService.ReopenBinding(project, 80L, 1L)));
        verify(templates).lockTemplate(argThat(query -> query.tenantId().equals(7L) && query.templateId().equals(8L)));
        verify(plans, never()).bindTemplateDraftIfCurrent(any());
    }

    @Test void reopenRejectsAnOlderPublishedRevision() {
        var latest = new ProjectTemplateRevisionDO(); latest.setId(81L); latest.setTenantId(7L);
        when(revisions.selectPublishedListByTemplateId(8L)).thenReturn(List.of(latest, revision));
        assertThrows(RuntimeException.class, () -> service.requireLatestForReopen(
                new ProjectPlanTemplateBindingService.ReopenBinding(project, 80L, 1L)));
        verify(matches, never()).evaluate(any(), any(), any(), any());
    }

    @Test void reopenRejectsTemplateWithoutS0Start() {
        when(revisions.selectPublishedListByTemplateId(8L)).thenReturn(List.of(revision));
        revision.setExecutionSnapshot("{\"executionSchemaVersion\":2,\"stages\":[{\"start\":true,\"lifecycleStage\":\"S1\"}]}");
        assertThrows(RuntimeException.class, () -> service.requireLatestForReopen(
                new ProjectPlanTemplateBindingService.ReopenBinding(project, 80L, 1L)));
        verify(matches, never()).evaluate(any(), any(), any(), any());
    }
}
