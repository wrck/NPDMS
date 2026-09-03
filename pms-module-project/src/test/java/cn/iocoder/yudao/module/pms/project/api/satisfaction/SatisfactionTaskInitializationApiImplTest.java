package cn.iocoder.yudao.module.pms.project.api.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionTaskInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionQuestionnaireTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.SatisfactionQuestionnaireTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    private SatisfactionTaskInitializationApiImpl api;
    private final AtomicReference<PlatformCommandExecutionApi.SuccessFacts> emitted = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        api = new SatisfactionTaskInitializationApiImpl(workBindingFactApi, projectScopeApi, taskMapper,
                questionnaireMapper, revisionMapper, commandExecutionApi);
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

        var result = api.initialize(command());

        assertEquals("CREATED", result.outcome());
        ArgumentCaptor<SatisfactionCollectionTaskDO> task = ArgumentCaptor.forClass(SatisfactionCollectionTaskDO.class);
        verify(taskMapper).insert((SatisfactionCollectionTaskDO) task.capture());
        assertEquals(1, task.getValue().getTaskRevisionNo());
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

    private static SatisfactionTaskInitializationCommand command() {
        return new SatisfactionTaskInitializationCommand(0L, 100L, 101L, 7, "ACC",
                "AcceptanceActivityCompletionFact", "500", 1L, "ACC",
                "AcceptanceActivityCompletionFact", "500", 1L, "op-1");
    }

    private static ProjectSatisfactionTaskFact taskFact() {
        return new ProjectSatisfactionTaskFact(100L, 101L, "T-SAT-SURVEY", 7,
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
        row.setVersion(0);
        return row;
    }
}
