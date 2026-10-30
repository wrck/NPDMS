package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverAssessmentDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistRevisionService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.ReviseCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverTaskRevisionServiceTest {
    @Test
    void revisesAssessmentWithoutChangingSubmittedAnswersAndReplaysOnce() {
        var f = new Fixture("P3", "SURVEYING");
        var command = f.command("P2");
        var result = f.service.revise(command);
        var replay = f.service.revise(command);
        assertEquals("P2", result.currentStage()); assertNull(f.task.getManualGrade()); assertTrue(replay.replayed());
        var draft = ArgumentCaptor.forClass(CutoverAssessmentDO.class);
        verify(f.assessments).insert(draft.capture());
        assertEquals(2L, draft.getValue().getAssessmentVersion());
        assertEquals("DRAFT", draft.getValue().getAssessmentStatus());
        assertEquals("HIGH", draft.getValue().getBusinessImportanceLevel());
        assertEquals("A", draft.getValue().getManualGrade());
        assertNull(draft.getValue().getSubmittedAt());
        assertEquals("SUBMITTED", f.assessment.getAssessmentStatus());
        assertEquals("HIGH", f.assessment.getBusinessImportanceLevel());
        verify(f.checklists).invalidateForRevisionIfMatch(any());
        verify(f.tasks, times(1)).reviseIfMatch(any());
        verifyNoInteractions(f.approvalService, f.planService, f.checklistCopies);
    }

    @Test
    void revisesChecklistAndInvalidatesOnlyTheUnapprovedPlan() {
        var f = new Fixture("P4", "PLAN_DRAFTING"); f.plan("DRAFT");
        var result = f.service.revise(f.command("P3"));
        assertEquals("P3", result.currentStage()); assertEquals("A", f.task.getManualGrade());
        verify(f.plans).invalidateForRevisionIfMatch(any());
        verify(f.checklistCopies).copyDraft(any());
        verify(f.assessments, never()).invalidateForRevisionIfMatch(any());
        verifyNoInteractions(f.approvalService);
    }

    @Test
    void withdrawsPendingApprovalBeforeInvalidatingAndDerivingPlan() {
        var f = new Fixture("P5", "APPROVING"); f.plan("SUBMITTED");
        when(f.approvalService.withdrawForRevision(any())).thenReturn(new CutoverApprovalFact(91L, 3L, 10L, 40L, 1,
                ApprovalStatus.WITHDRAWN, 1, null, null, null));
        var result = f.service.revise(f.command("P4"));
        assertEquals("P4", result.currentStage());
        var order = inOrder(f.approvalService, f.plans, f.tasks, f.planService);
        order.verify(f.approvalService).withdrawForRevision(any());
        order.verify(f.plans).invalidateForRevisionIfMatch(any());
        order.verify(f.tasks).reviseIfMatch(any());
        order.verify(f.planService).revise(any());
        verify(f.assessments, never()).invalidateForRevisionIfMatch(any());
        verify(f.checklists, never()).invalidateForRevisionIfMatch(any());
    }

    @Test
    void refusesApprovedArchivedWrongOwnerAndStaleVersionsBeforeMutation() {
        for (String status : List.of("CLOSURE_IN_PROGRESS", "ARCHIVED")) {
            var f = new Fixture("P6", status);
            assertThrows(CutoverTaskApplicationException.class, () -> f.service.revise(f.command("P2")));
            verify(f.tasks, never()).reviseIfMatch(any()); verifyNoInteractions(f.approvalService);
        }
        var f = new Fixture("P3", "SURVEYING"); f.task.setOwnerUserId(9L);
        assertThrows(CutoverTaskApplicationException.class, () -> f.service.revise(f.command("P2")));
        f.task.setOwnerUserId(8L); f.task.setVersion(5L);
        assertThrows(CutoverTaskApplicationException.class, () -> f.service.revise(f.command("P2")));
        verify(f.assessments, never()).invalidateForRevisionIfMatch(any());
        verify(f.tasks, never()).reviseIfMatch(any());
    }

    @Test
    void doesNotInvalidateSourcesWhenWithdrawalFails() {
        var f = new Fixture("P5", "APPROVING"); f.plan("SUBMITTED");
        when(f.approvalService.withdrawForRevision(any())).thenThrow(new IllegalStateException("workflow unavailable"));
        assertThrows(IllegalStateException.class, () -> f.service.revise(f.command("P2")));
        verify(f.plans, never()).invalidateForRevisionIfMatch(any());
        verify(f.assessments, never()).invalidateForRevisionIfMatch(any());
        verify(f.tasks, never()).reviseIfMatch(any());
    }

    @Test
    void adoptsOnlyExplicitlySelectedEffectiveConfigurationAndRequiresNewMatching() {
        var f = new Fixture("P4", "PLAN_DRAFTING"); f.plan("DRAFT");
        var configuration = f.configuration("PUBLISHED");
        var command = f.command("P3", configuration.getId());
        var result = f.service.revise(command);
        assertEquals("P3", result.currentStage());
        assertEquals(101L, f.task.getConfigurationRevisionId());
        verify(f.configurationPolicy).validate(configuration, "CONFIGURATION_CHANGE", null);
        verify(f.checklists).invalidateForRevisionIfMatch(any());
        verifyNoInteractions(f.checklistCopies);
        assertTrue(f.service.revise(command).replayed());
        verify(f.tasks, times(1)).reviseIfMatch(any());
    }

    @Test
    void rejectsUnpublishedExpiredAndUnrelatedConfigurationsBeforeInvalidation() {
        for (String kind : List.of("DRAFT", "DISABLED", "EXPIRED", "OTHER_CODE")) {
            var f = new Fixture("P4", "PLAN_DRAFTING"); f.plan("DRAFT");
            var configuration = f.configuration("PUBLISHED");
            if (kind.equals("EXPIRED")) configuration.setEffectiveTo(LocalDateTime.now().minusMinutes(1));
            else if (kind.equals("OTHER_CODE")) configuration.setConfigurationCode("OTHER");
            else configuration.setStatusCode(kind);
            assertThrows(CutoverTaskApplicationException.class, () -> f.service.revise(f.command("P3", 101L)));
            verify(f.tasks, never()).reviseIfMatch(any());
            verify(f.checklists, never()).invalidateForRevisionIfMatch(any());
            verify(f.plans, never()).invalidateForRevisionIfMatch(any());
        }
    }

    private static final class Fixture {
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final CutoverAssessmentMapper assessments = mock(CutoverAssessmentMapper.class);
        final CutoverAssessmentDeviceMapper devices = mock(CutoverAssessmentDeviceMapper.class);
        final CutoverChecklistMapper checklists = mock(CutoverChecklistMapper.class);
        final CutoverPlanRevisionMapper plans = mock(CutoverPlanRevisionMapper.class);
        final CutoverApprovalInstanceMapper approvals = mock(CutoverApprovalInstanceMapper.class);
        final CutoverTaskStageHistoryMapper history = mock(CutoverTaskStageHistoryMapper.class);
        final CutoverProjectScopePort scope = mock(CutoverProjectScopePort.class);
        final CutoverApprovalApplicationService approvalService = mock(CutoverApprovalApplicationService.class);
        final CutoverChecklistRevisionService checklistCopies = mock(CutoverChecklistRevisionService.class);
        final CutoverPlanApplicationService planService = mock(CutoverPlanApplicationService.class);
        final CutoverConfigurationRevisionMapper configurations = mock(CutoverConfigurationRevisionMapper.class);
        final CutoverIntakeConfigurationPolicy configurationPolicy = mock(CutoverIntakeConfigurationPolicy.class);
        final CutoverTaskDO task = new CutoverTaskDO();
        final CutoverAssessmentDO assessment = new CutoverAssessmentDO();
        final CutoverTaskRevisionService service;

        Fixture(String stage, String status) {
            task.setId(10L); task.setTenantId(1L); task.setTaskNo("REVISION-10"); task.setTaskOrigin("NEW_PLATFORM");
            task.setOwnerUserId(8L); task.setProjectId(2L); task.setProjectScopeVersion(7L);
            task.setConfigurationRevisionId(100L); task.setConfigurationCode("CUTOVER_STANDARD_V1"); task.setConfigurationRevisionNo(1);
            task.setCutoverType("CONFIGURATION_CHANGE");
            task.setCurrentStage(stage); task.setTaskStatus(status); task.setManualGrade("A"); task.setCurrentAssessmentId(20L); task.setVersion(4L);
            assessment.setId(20L); assessment.setTenantId(1L); assessment.setCutoverTaskId(10L); assessment.setVersion(0L);
            assessment.setAssessmentVersion(1L); assessment.setAssessmentStatus("SUBMITTED"); assessment.setManualGrade("A");
            assessment.setBusinessImportanceLevel("HIGH"); assessment.setSubmittedAt(LocalDateTime.now());
            var checklist = new CutoverChecklistDO(); checklist.setId(30L); checklist.setTenantId(1L);
            checklist.setCutoverTaskId(10L); checklist.setStatusCode("SUBMITTED"); checklist.setVersion(0L);
            when(tasks.selectById(10L)).thenReturn(task); when(tasks.selectForUpdate(any())).thenReturn(task);
            when(tasks.reviseIfMatch(any())).thenReturn(1); when(tasks.selectMaxStageHistorySequence(any())).thenReturn(5);
            when(assessments.selectForUpdate(any())).thenReturn(assessment); when(assessments.invalidateForRevisionIfMatch(any())).thenReturn(1);
            when(assessments.insert(any(CutoverAssessmentDO.class))).thenReturn(1); when(devices.selectListByParents(any())).thenReturn(List.of());
            when(checklists.selectCurrentForUpdate(any())).thenReturn(checklist); when(checklists.invalidateForRevisionIfMatch(any())).thenReturn(1);
            when(plans.invalidateForRevisionIfMatch(any())).thenReturn(1); when(history.insert(any(CutoverTaskStageHistoryDO.class))).thenReturn(1);
            var fact = new CutoverProjectScopePort.ProjectScopeFact(2L, 7L, true);
            when(scope.inspect(8L, 2L, "ACTION_EDIT")).thenReturn(fact); when(scope.lockAndRevalidate(any())).thenReturn(fact);
            service = new CutoverTaskRevisionService(tasks, assessments, devices, checklists, plans, approvals, history,
                    scope, approvalService, checklistCopies, planService, new Commands(), configurations, configurationPolicy);
        }

        void plan(String status) {
            var plan = new CutoverPlanRevisionDO(); plan.setId(40L); plan.setTenantId(1L); plan.setCutoverTaskId(10L);
            plan.setStatusCode(status); plan.setVersion(0L); when(plans.selectCurrentForUpdate(any())).thenReturn(plan);
        }
        ReviseCutoverTaskCommand command(String target) {
            return command(target, null);
        }
        ReviseCutoverTaskCommand command(String target, Long configurationId) {
            return ReviseCutoverTaskCommand.builder().tenantId(1L).actorId(8L).taskId(10L).expectedTaskVersion(4L)
                    .expectedProjectScopeVersion(7L).targetStage(target).configurationRevisionId(configurationId)
                    .reason("修正现场实施信息").idempotencyKey("revision").correlationId("revision").build();
        }
        CutoverConfigurationRevisionDO configuration(String status) {
            var row = new CutoverConfigurationRevisionDO(); row.setId(101L); row.setTenantId(1L);
            row.setConfigurationCode("CUTOVER_STANDARD_V1"); row.setRevisionNo(2); row.setStatusCode(status);
            row.setEffectiveFrom(LocalDateTime.now().minusDays(1));
            when(configurations.selectForUpdate(any())).thenReturn(row); return row;
        }
    }

    private static final class Commands implements PlatformCommandExecutionApi {
        private Object response;
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String digest, Class<T> type,
                                              Supplier<T> operation, Function<T, SuccessFacts> facts) {
            if (response != null) return new ExecutionResult<>(Decision.REPLAY_COMPLETED, type.cast(response));
            T result = operation.get(); response = result; return new ExecutionResult<>(Decision.NEW, result);
        }
    }
}
