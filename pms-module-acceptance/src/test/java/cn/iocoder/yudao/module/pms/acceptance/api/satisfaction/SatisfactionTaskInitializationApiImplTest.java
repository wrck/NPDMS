package cn.iocoder.yudao.module.pms.acceptance.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto.SatisfactionTaskInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatisfactionTaskInitializationApiImplTest {

    @Mock private ProjectWorkBindingFactApi workBindingFactApi;
    @Mock private ProjectScopeApi projectScopeApi;
    @Mock private SatisfactionCollectionTaskMapper taskMapper;
    @Mock private SatisfactionQuestionnaireMapper questionnaireMapper;
    @Mock private SatisfactionQuestionnaireTemplateRevisionMapper revisionMapper;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private PlatformDeliveryRequirementApi platform;
    private SatisfactionTaskInitializationApiImpl api;
    private final AtomicReference<PlatformCommandExecutionApi.SuccessFacts> emitted = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        api = new SatisfactionTaskInitializationApiImpl(workBindingFactApi, projectScopeApi, taskMapper,
                questionnaireMapper, revisionMapper, commandExecutionApi, platform);
        org.mockito.Mockito.lenient().when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(3);
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            Object response = operation.get();
            emitted.set(facts.apply(response));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsRevisionOneFromLockedOwnerFactsAndProjectTreeVersion() {
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(taskFact());
        when(taskMapper.selectByTriggerForUpdate(any())).thenReturn(null);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L, 9L,
                Set.of(100L), Set.of()));
        when(revisionMapper.selectFrozenRevision(any())).thenReturn(revision());
        when(platform.lockByTask(100L, "CUSTOM-SAT")).thenReturn(List.of(deliverable()));

        var result = api.initialize(command());

        assertEquals("CREATED", result.outcome());
        ArgumentCaptor<SatisfactionCollectionTaskDO> task = ArgumentCaptor.forClass(SatisfactionCollectionTaskDO.class);
        verify(taskMapper).insert((SatisfactionCollectionTaskDO) task.capture());
        assertEquals(1, task.getValue().getTaskRevisionNo());
        assertEquals(400L, task.getValue().getDeliverableId());
        assertEquals(1000L, task.getValue().getAssignedToUserId());
        ArgumentCaptor<SatisfactionQuestionnaireDO> questionnaire =
                ArgumentCaptor.forClass(SatisfactionQuestionnaireDO.class);
        verify(questionnaireMapper).insert((SatisfactionQuestionnaireDO) questionnaire.capture());
        assertEquals(9L, questionnaire.getValue().getAccessScopeVersion());
        assertEquals("[{\"code\":\"Q1\"}]", questionnaire.getValue().getFrozenQuestionJson());
        assertEquals("SatisfactionTaskCreated", emitted.get().businessEvents().getFirst().eventType());
        org.junit.jupiter.api.Assertions.assertTrue(emitted.get().businessEvents().getFirst().eventPayload()
                .contains("\"projectTaskVersion\":7"));
    }

    @Test
    void replaysSameTriggerAndRejectsDifferentSourceWithoutWrites() {
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(taskFact());
        SatisfactionCollectionTaskDO existing = existingTask();
        when(taskMapper.selectByTriggerForUpdate(any())).thenReturn(existing);
        assertEquals("REPLAYED", api.initialize(command()).outcome());

        existing.setSourceObjectId("different");
        assertEquals("FACT_CONFLICT", api.initialize(command()).outcome());
        verify(questionnaireMapper, never()).insert((SatisfactionQuestionnaireDO) any());
    }

    @Test
    void rejectsWrongTimingBeforeAnyAccWrite() {
        ProjectSatisfactionTaskFact fact = taskFact();
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(
                new ProjectSatisfactionTaskFact(fact.projectId(), fact.projectTaskId(), fact.taskCode(),
                        fact.projectTaskVersion(), "AFTER_FINAL_ACCEPTANCE", fact.templateId(),
                        fact.templateRevisionId(), fact.templateVersion(), fact.ruleVersion(), fact.threshold(),
                        fact.currentAssigneeUserId()));
        assertEquals("FACT_CONFLICT", api.initialize(command()).outcome());
        verify(taskMapper, never()).selectByTriggerForUpdate(any());
        verify(taskMapper, never()).insert((SatisfactionCollectionTaskDO) any());
    }

    @Test
    void manualStartUsesFrozenTemplateBeforeConfiguredTiming() {
        var original = taskFact();
        var fact = new ProjectSatisfactionTaskFact(original.projectId(), original.projectTaskId(), original.taskCode(),
                original.projectTaskVersion(), "AFTER_FINAL_ACCEPTANCE", original.templateId(),
                original.templateRevisionId(), original.templateVersion(), original.ruleVersion(),
                original.threshold(), original.currentAssigneeUserId());
        when(workBindingFactApi.lockCurrentSatisfactionTaskByProject(any())).thenReturn(fact);
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(fact);
        var scope = new ProjectScopeResult(100L, 9L, Set.of(100L), Set.of());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope);
        when(revisionMapper.selectFrozenRevision(any())).thenReturn(revision());
        when(platform.lockByTask(100L, "CUSTOM-SAT")).thenReturn(List.of(deliverable()));
        assertEquals("CREATED", api.startManual(100L, 2000L, "manual-1").outcome());
        var task = ArgumentCaptor.forClass(SatisfactionCollectionTaskDO.class);
        verify(taskMapper).insert(task.capture());
        assertEquals("SatisfactionManualInitiation", task.getValue().getSourceObjectType());
        assertEquals(1000L, task.getValue().getAssignedToUserId());
        var scopeCaptor = ArgumentCaptor.forClass(PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scopeCaptor.capture(), any(), any(), any(), any());
        assertEquals(2000L, scopeCaptor.getValue().actorId());
    }

    @Test
    void manualStartRejectsMissingProjectEditScopeBeforeReadingFrozenFacts() {
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L, 9L, Set.of(), Set.of()));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> api.startManual(100L, 2000L, "denied"));
        org.mockito.Mockito.verifyNoInteractions(workBindingFactApi, taskMapper, questionnaireMapper);
    }

    @Test
    void laterAutomaticTriggerReusesManualFirstRoundWithoutWritesOrDuplicateEvents() {
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(taskFact());
        var first = existingTask();
        first.setSourceObjectType("SatisfactionManualInitiation");
        first.setSourceObjectId("100");
        when(taskMapper.selectFirstByProjectForUpdate(any())).thenReturn(first);
        assertEquals("REPLAYED", api.initialize(command()).outcome());
        assertEquals(List.of(), emitted.get().businessEvents());
        verify(taskMapper, never()).insert(any(SatisfactionCollectionTaskDO.class));
        org.mockito.Mockito.verifyNoInteractions(questionnaireMapper, revisionMapper, platform);
    }

    @Test
    void manualStartReusesExistingAutomaticRoundWithoutChangingHistory() {
        when(workBindingFactApi.lockCurrentSatisfactionTaskByProject(any())).thenReturn(taskFact());
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(taskFact());
        var scope = new ProjectScopeResult(100L, 9L, Set.of(100L), Set.of());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope);
        when(taskMapper.selectFirstByProjectForUpdate(any())).thenReturn(existingTask());
        assertEquals(200L, api.startManual(100L, 2000L, "manual-2").taskId());
        assertEquals(List.of(), emitted.get().businessEvents());
        verify(taskMapper, never()).insert(any(SatisfactionCollectionTaskDO.class));
        org.mockito.Mockito.verifyNoInteractions(questionnaireMapper, revisionMapper, platform);
    }

    @Test
    void rejectsAbsentOrAmbiguousDeliverableBeforeOwnerWrites() {
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(taskFact());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L, 9L,
                Set.of(100L), Set.of()));
        when(revisionMapper.selectFrozenRevision(any())).thenReturn(revision());
        when(platform.lockByTask(100L, "CUSTOM-SAT"))
                .thenReturn(List.of(), List.of(deliverable(), deliverable()));
        for (int i = 0; i < 2; i++) {
            assertEquals("SATISFACTION_DELIVERABLE_BINDING_NOT_UNIQUE",
                    org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                            () -> api.initialize(command())).getMessage());
        }
        org.mockito.Mockito.verifyNoInteractions(questionnaireMapper);
        verify(taskMapper, never()).insert(any(SatisfactionCollectionTaskDO.class));
    }

    @Test
    void misboundDeliverableCannotInitializeTheCollectionTask() {
        when(workBindingFactApi.lockAndRevalidateSatisfactionTask(any())).thenReturn(taskFact());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(new ProjectScopeResult(100L, 9L,
                Set.of(100L), Set.of()));
        when(revisionMapper.selectFrozenRevision(any())).thenReturn(revision());
        when(platform.lockByTask(100L, "CUSTOM-SAT")).thenReturn(List.of(
                new TemplateFrozenView(400L, 101L, "CUSTOM-RESULT", "满意度报告", "S5", "CUSTOM-SAT",
                        null, null, false, 0, null, "OPEN", "{}", 0)));
        assertEquals("SATISFACTION_DELIVERABLE_BINDING_CONFLICT",
                org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                        () -> api.initialize(command())).getMessage());
        verify(taskMapper, never()).insert(any(SatisfactionCollectionTaskDO.class));
    }

    private static TemplateFrozenView deliverable() {
        return new TemplateFrozenView(400L, 100L, "CUSTOM-RESULT", "满意度调查报告", "S5", "CUSTOM-SAT",
                null, null, false, 0, null, "OPEN", "{}", 0);
    }

    private static SatisfactionTaskInitializationCommand command() {
        return new SatisfactionTaskInitializationCommand(0L, 100L, 101L, 7, "ACC",
                "AcceptanceActivityCompletionFact", "500", 1L, "ACC",
                "AcceptanceActivityCompletionFact", "500", 1L, "op-1");
    }

    private static ProjectSatisfactionTaskFact taskFact() {
        return new ProjectSatisfactionTaskFact(100L, 101L, "CUSTOM-SAT", 7,
                "AFTER_INITIAL_ACCEPTANCE", 900L, 901L, 1, "RULE-V1",
                new BigDecimal("80.00"), 1000L);
    }

    private static SatisfactionQuestionnaireTemplateRevisionDO revision() {
        SatisfactionQuestionnaireTemplateRevisionDO row = new SatisfactionQuestionnaireTemplateRevisionDO();
        row.setId(901L);
        row.setTemplateId(900L);
        row.setRevisionNo(1);
        row.setRuleVersion("RULE-V1");
        row.setFrozenThreshold(new BigDecimal("80.00"));
        row.setFrozenQuestionJson("[{\"code\":\"Q1\"}]");
        return row;
    }

    private static SatisfactionCollectionTaskDO existingTask() {
        SatisfactionCollectionTaskDO row = new SatisfactionCollectionTaskDO();
        row.setId(200L);
        row.setProjectId(100L);
        row.setProjectTaskId(101L);
        row.setSourceOwnerContext("ACC");
        row.setSourceObjectType("AcceptanceActivityCompletionFact");
        row.setSourceObjectId("500");
        row.setSourceObjectVersion(1L);
        row.setQuestionnaireId(201L);
        row.setCollectionKey("SAT-200");
        row.setTaskRevisionNo(1);
        row.setVersion(0L);
        return row;
    }
}
