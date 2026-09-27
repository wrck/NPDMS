package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.function.Function;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndependentSatisfactionServiceTest {
    final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    final SatisfactionQuestionnaireMapper questionnaires = mock(SatisfactionQuestionnaireMapper.class);
    final SatisfactionQuestionnaireTemplateRevisionMapper revisions = mock(SatisfactionQuestionnaireTemplateRevisionMapper.class);
    final ProjectAcceptanceContextApi projects = mock(ProjectAcceptanceContextApi.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final IndependentSatisfactionService service = new IndependentSatisfactionService(tasks, questionnaires, revisions, projects, commands, permissions);
    final IndependentSatisfactionService.Create request = new IndependentSatisfactionService.Create(80L, 20L, 21L, 4L, 3L);
    final ProjectAcceptanceContextApi.Context context = new ProjectAcceptanceContextApi.Context(80L, 80L, 4L, 3L, "ACTIVE");
    final SatisfactionQuestionnaireTemplateRevisionDO revision = new SatisfactionQuestionnaireTemplateRevisionDO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(permissions.hasAnyPermissions(19L, "pms:acceptance:satisfaction:manage")).thenReturn(true);
        when(projects.inspect(any())).thenReturn(context);
        when(projects.lock(any(), eq(4L), eq(3L))).thenReturn(context);
        revision.setId(21L); revision.setTenantId(7L); revision.setTemplateId(20L); revision.setRevisionNo(1);
        revision.setRevisionStatus("PUBLISHED"); revision.setRuleVersion("SUM_V1"); revision.setFrozenThreshold(new BigDecimal("80"));
        revision.setFrozenQuestionJson("""
            {"schemaVersion":1,"questions":[{"code":"Q1","title":"满意度","type":"SINGLE_CHOICE","required":true,
             "options":[{"code":"LOW","label":"需整改","score":"20.00"},{"code":"HIGH","label":"满意","score":"100.00"}]}],
             "scoring":{"ruleVersion":"SUM_V1","strategy":"SUM_V1","scoreMin":"0.00","scoreMax":"100.00",
             "precision":2,"roundingMode":"HALF_UP","threshold":"80.00"}}
            """);
        when(revisions.selectFrozenRevision(any())).thenReturn(revision);
        when(tasks.insert(any(SatisfactionCollectionTaskDO.class))).thenReturn(1);
        when(questionnaires.insert(any(SatisfactionQuestionnaireDO.class))).thenReturn(1);
        when(commands.execute(any(), any(), any(), any(), any())).thenAnswer(call -> {
            var result = call.<Supplier<IndependentSatisfactionService.Created>>getArgument(3).get();
            var audit = call.<Function<IndependentSatisfactionService.Created, PlatformCommandExecutionApi.SuccessFacts>>getArgument(4).apply(result);
            assertEquals(result.taskId().toString(), audit.resourceKey());
            assertTrue(audit.businessEvents().isEmpty(), "Starting collection cannot publish a passing result");
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }
    @AfterEach void clean() { TenantContextHolder.clear(); }

    @Test void freezesPublishedQuestionnaireWithoutInventingTemplateTaskOrDelivery() {
        var created = service.create(7L, 19L, request, "request-1");
        var saved = ArgumentCaptor.forClass(SatisfactionCollectionTaskDO.class);
        var frozen = ArgumentCaptor.forClass(SatisfactionQuestionnaireDO.class);
        verify(tasks).insert(saved.capture()); verify(questionnaires).insert(frozen.capture());
        var task = saved.getValue(); var questionnaire = frozen.getValue();
        assertEquals(created.taskId(), task.getId()); assertEquals("DIRECT", task.getOriginKind());
        assertEquals("19:request-1", task.getOriginKey()); assertEquals(80L, task.getProjectId());
        assertNull(task.getProjectTaskId()); assertNull(task.getDeliverableId());
        assertEquals("PENDING_COLLECTION", task.getTaskStatus()); assertEquals(19L, task.getAssignedToUserId());
        assertEquals(task.getId(), questionnaire.getCollectionTaskId()); assertEquals(21L, questionnaire.getTemplateRevisionId());
        assertEquals(revision.getFrozenQuestionJson(), questionnaire.getFrozenQuestionJson());
        assertEquals(new BigDecimal("80"), questionnaire.getFrozenThreshold()); assertEquals(3L, questionnaire.getAccessScopeVersion());
        var order = inOrder(projects, tasks, questionnaires);
        order.verify(projects).lock(any(), eq(4L), eq(3L)); order.verify(tasks).insert(any(SatisfactionCollectionTaskDO.class));
        order.verify(questionnaires).insert(any(SatisfactionQuestionnaireDO.class));
    }
    @Test void foreignTenantAndUnauthorizedActorCannotEnterTheCommand() {
        when(permissions.hasAnyPermissions(19L, "pms:acceptance:satisfaction:manage")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.create(7L, 19L, request, "key"));
        assertThrows(RuntimeException.class, () -> service.create(8L, 19L, request, "key"));
        verifyNoInteractions(commands, projects, tasks, questionnaires, revisions);
    }
    @Test void closedProjectAndStaleVersionCannotFreezeAQuestionnaire() {
        when(projects.lock(any(), eq(4L), eq(3L))).thenReturn(new ProjectAcceptanceContextApi.Context(80L, 80L, 4L, 3L, "NORMAL_CLOSED"));
        assertThrows(RuntimeException.class, () -> service.create(7L, 19L, request, "key"));
        when(projects.lock(any(), eq(4L), eq(3L))).thenThrow(new IllegalStateException("STALE_PROJECT"));
        assertThrows(RuntimeException.class, () -> service.create(7L, 19L, request, "key"));
        verifyNoInteractions(tasks, questionnaires, revisions);
    }
    @Test void unPublishedForeignAndInconsistentQuestionnairesCannotCreate() {
        revision.setRevisionStatus("DRAFT");
        assertThrows(RuntimeException.class, () -> service.create(7L, 19L, request, "key"));
        revision.setRevisionStatus("PUBLISHED"); revision.setTenantId(8L);
        assertThrows(RuntimeException.class, () -> service.create(7L, 19L, request, "key"));
        revision.setTenantId(7L); revision.setFrozenThreshold(new BigDecimal("20"));
        assertThrows(RuntimeException.class, () -> service.create(7L, 19L, request, "key"));
        verifyNoInteractions(tasks, questionnaires);
    }
    @Test void replayDoesNotInsertAndConflictDoesNotChangeOldCollection() {
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,
                new IndependentSatisfactionService.Created(30L, 31L, "SAT-30"))).when(commands).execute(any(), any(), any(), any(), any());
        assertEquals(30L, service.create(7L, 19L, request, "key").taskId());
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.CONFLICT, null))
                .when(commands).execute(any(), any(), any(), any(), any());
        assertThrows(RuntimeException.class, () -> service.create(7L, 19L, request, "key"));
        verifyNoInteractions(tasks, questionnaires, revisions);
    }
    @Test void directMutationsRejectClosedProjectAndMixedLegacyIdentity() {
        var task = new SatisfactionCollectionTaskDO(); task.setTenantId(7L); task.setProjectId(80L); task.setOriginKind("DIRECT");
        when(projects.lock(any(), eq(4L), eq(3L))).thenReturn(new ProjectAcceptanceContextApi.Context(80L, 80L, 4L, 3L, "NORMAL_CLOSED"));
        assertThrows(RuntimeException.class, () -> service.lockIfDirect(7L, 19L, task));
        task.setProjectTaskId(90L);
        assertThrows(RuntimeException.class, () -> service.lockIfDirect(7L, 19L, task));
    }
}
