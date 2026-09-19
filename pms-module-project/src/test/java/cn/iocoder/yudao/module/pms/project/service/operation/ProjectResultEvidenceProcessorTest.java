package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectStageCompletionService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectTaskAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实扫描、身份读取、Mapper XML与事务代理；准入和完成服务使用明确的外围替身。 */
class ProjectResultEvidenceProcessorTest {
    private ResultEvidenceScanFixture f;
    private ProjectResultEvidenceProcessor processor;
    private final ProjectTaskAdmissionService taskAdmission = mock(ProjectTaskAdmissionService.class);
    private final ProjectStageAdmissionService stageAdmission = mock(ProjectStageAdmissionService.class);
    private final ProjectTaskBusinessAssociationService associations = mock(ProjectTaskBusinessAssociationService.class);
    private final ProjectTaskLifecycleService tasks = mock(ProjectTaskLifecycleService.class);
    private final ProjectStageCompletionService stages = mock(ProjectStageCompletionService.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);

    @BeforeEach void before() throws Exception {
        f = new ResultEvidenceScanFixture();
        processor = f.recovery.proxy(new ProjectResultEvidenceProcessor(f.recovery.contexts, f.evidence,
                taskAdmission, stageAdmission, associations, tasks, stages, f.recovery.outbox, audit));
        @SuppressWarnings("unchecked") ObjectProvider<ProjectResultEvidenceProcessor> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(processor);
        ReflectionTestUtils.setField(f.recovery.delivery, "evaluated", provider);
        when(taskAdmission.activateEligible(any(), any(), any())).thenReturn(new ProjectTaskAdmissionService.Result(false, false));
        when(stageAdmission.activateStage(any(), isNull(), any(), any())).thenReturn(List.of(
                new ProjectStageAdmissionService.StageAdmission(4L, "stage", RuleEvaluation.Outcome.MATCHED, null, false)));
        when(tasks.completeFromBusinessResult(any(), any(), any())).thenReturn(new ProjectTaskLifecycleService.AutomaticResult(false, false));
        when(stages.completeStage(any(), any(), isNull(), any())).thenReturn(new ProjectStageCompletionService.Completion(0, false));
        f.recovery.jdbc.execute("CREATE TABLE formal_writer_test(id BIGINT PRIMARY KEY)");
    }
    @AfterEach void after() { f.close(); }

    private ResultEvidenceEvaluatedEvent evaluated() {
        return ResultEvidenceEvaluatedEvent.create(ResultSubscriptionWakeup.create(f.recovery.row()), f.scan());
    }
    private void satisfy() { f.seed(1, "object", "r1", null, Validity.CURRENT); f.tick(); }
    private void task() {
        f.recovery.plan.setExecutionSnapshot(JsonUtils.toJsonString(ResultSubscriptionTaskFixture.snapshot()));
        f.recovery.round.setNodeKind("TASK"); f.recovery.round.setNodeKey("task");
        f.recovery.jdbc.update("UPDATE proj_result_subscription SET node_kind='TASK',node_key='task'");
    }

    @Test void satisfiedStageNotifiesOnlyTheOriginalFormalWriterAndNeverAnOwnerCommand() {
        satisfy(); var event = evaluated();
        when(stages.completeStage(3L, 4L, null, event.eventId())).thenReturn(new ProjectStageCompletionService.Completion(1, false));
        assertTrue(f.recovery.delivery.deliver(new PlatformOutboxMessageDTO(event.eventId(), ResultEvidenceEvaluatedEvent.EVENT_TYPE,
                JsonUtils.toJsonString(event), 0, 1L, LocalDateTime.now())));
        verify(stages).completeStage(3L, 4L, null, event.eventId());
        verifyNoInteractions(tasks, taskAdmission, associations);
        assertEquals(1, f.events(cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested.EVENT_TYPE));
        f.recovery.round.setEndedAt(LocalDateTime.now()); f.recovery.round.setStatus("DONE");
        assertEquals("HISTORICAL_RECIPIENT", processor.process(event));
        verify(stages, times(1)).completeStage(any(), any(), any(), any());
    }

    @Test void pureTaskWaitsForTheFormalAggregateEvidenceDecisionEvenWhenOneSubscriptionMatched() {
        task(); satisfy(); var event = evaluated();
        assertEquals("REEVALUATED_WAITING", processor.process(event));
        verify(tasks).completeFromBusinessResult(3L, 4L, event.eventId());
        verifyNoInteractions(associations, stageAdmission, stages, audit);
        when(tasks.completeFromBusinessResult(3L, 4L, event.eventId())).thenReturn(new ProjectTaskLifecycleService.AutomaticResult(true, false));
        assertEquals("ADVANCED", processor.process(event));
        verifyNoInteractions(associations);
    }

    @Test void waitingAndPendingAdmissionDoNotInventBusinessOrNodeCompletion() {
        f.tick(); assertEquals("WAITING_EVIDENCE", processor.process(evaluated()));
        verifyNoInteractions(tasks, stages, taskAdmission, stageAdmission);
        f.advanceEpoch(8); satisfy(); f.recovery.round.setStatus("PENDING");
        assertEquals("WAITING_ADMISSION", processor.process(evaluated()));
        verify(stages, never()).completeStage(any(), any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"obsolete-scan", "retired", "closed", "superseded", "ended"})
    void staleOrHistoricalRecipientsCannotAdvanceAnotherRound(String damage) {
        satisfy(); var event = evaluated();
        switch (damage) {
            case "obsolete-scan" -> f.advanceEpoch(8);
            case "retired" -> f.recovery.jdbc.update("UPDATE proj_result_subscription SET phase='RETIRED'");
            case "closed" -> f.recovery.project.setLifecycleStatus("CLOSED");
            case "superseded" -> f.recovery.plan.setStatus("SUPERSEDED");
            case "ended" -> { f.recovery.round.setStatus("DONE"); f.recovery.round.setEndedAt(LocalDateTime.now()); }
            default -> throw new AssertionError(damage);
        }
        assertNotEquals("ADVANCED", processor.process(event));
        verifyNoInteractions(tasks, stages, taskAdmission, stageAdmission, associations, audit);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "other-subscription", "future", "collecting", "status"})
    void invalidScanIdentityOrConclusionIsNotAcknowledgedAsASuccess(String damage) {
        satisfy(); var event = evaluated();
        switch (damage) {
            case "missing" -> f.recovery.jdbc.update("DELETE FROM proj_result_evidence_scan");
            case "other-subscription" -> f.recovery.jdbc.update("UPDATE proj_result_evidence_scan SET subscription_id=999");
            case "future" -> f.recovery.jdbc.update("UPDATE proj_result_evidence_scan SET subscription_version=99");
            case "collecting" -> f.recovery.jdbc.update("UPDATE proj_result_evidence_scan SET status='COLLECTING'");
            case "status" -> f.recovery.jdbc.update("UPDATE proj_result_evidence_scan SET status='WAITING'");
            default -> throw new AssertionError(damage);
        }
        assertThrows(RuntimeException.class, () -> processor.process(event));
        verifyNoInteractions(tasks, stages, taskAdmission, stageAdmission, associations, audit);
    }

    @ParameterizedTest @ValueSource(strings = {"admission", "completion", "outbox"})
    void uncertainDependenciesAndOutboxFailureRollBackThisRecipient(String damage) {
        task(); satisfy(); var event = evaluated();
        if (damage.equals("admission"))
            when(taskAdmission.activateEligible(any(), any(), any())).thenReturn(new ProjectTaskAdmissionService.Result(false, true));
        else when(tasks.completeFromBusinessResult(any(), any(), any())).thenAnswer(call -> {
            f.recovery.jdbc.update("INSERT INTO formal_writer_test VALUES(1)");
            return new ProjectTaskLifecycleService.AutomaticResult(!damage.equals("completion"), damage.equals("completion"));
        });
        f.recovery.failOutbox = damage.equals("outbox");
        assertThrows(IllegalStateException.class, () -> processor.process(event));
        assertEquals(0, f.recovery.jdbc.queryForObject("SELECT COUNT(*) FROM formal_writer_test", Integer.class));
        assertEquals(0, f.events(cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested.EVENT_TYPE));
    }

    @ParameterizedTest @ValueSource(strings = {"schema", "scan-string", "scan-fraction", "scan-overflow", "missing-target", "tenant", "extra", "event-id"})
    void transportChecksStoredTokensBeforeCallingTheProcessor(String damage) {
        satisfy(); var event = evaluated();
        var json = (ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(event));
        switch (damage) {
            case "schema" -> json.put("eventVersion", "1");
            case "scan-string" -> json.put("scanId", event.scanId().toString());
            case "scan-fraction" -> json.put("scanId", 1.5);
            case "scan-overflow" -> json.set("scanId", JsonUtils.parseTree("999999999999999999999999999"));
            case "missing-target" -> json.remove("target");
            case "tenant" -> ((ObjectNode) json.get("target")).put("tenantId", 2);
            case "extra" -> json.put("actorId", 999);
            case "event-id" -> json.put("eventId", UUID.randomUUID().toString());
            default -> throw new AssertionError(damage);
        }
        assertThrows(RuntimeException.class, () -> f.recovery.delivery.deliver(new PlatformOutboxMessageDTO(
                event.eventId(), ResultEvidenceEvaluatedEvent.EVENT_TYPE, json.toString(), 0, 1L, LocalDateTime.now())));
        verifyNoInteractions(tasks, stages, taskAdmission, stageAdmission, associations, audit);
    }
}
