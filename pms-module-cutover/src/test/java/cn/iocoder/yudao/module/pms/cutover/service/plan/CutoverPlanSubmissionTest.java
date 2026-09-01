package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskPlanSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskSourceInvalidationUpdate;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.DownloadCutoverPlanDraftCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SubmitCutoverPlanCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.InvalidateCutoverPlanSourceCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.DownloadCutoverPlanDraftResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.InvalidateCutoverPlanSourceResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverPlanSubmissionTest {

    @Test
    void downloadsCompleteSimpleDraftWithoutAdvancingTaskOrPlan() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        CutoverSupportArrangementMapper supportMapper = mock(CutoverSupportArrangementMapper.class);
        CutoverProjectScopePort projectScope = mock(CutoverProjectScopePort.class);
        CutoverPlanFilePort filePort = mock(CutoverPlanFilePort.class);
        CutoverTaskDO task = task();
        CutoverPlanRevisionDO plan = simplePlan();
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(projectScope.inspect(8L, 70L, "ACTION_VIEW"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(planMapper.selectCurrent(any())).thenReturn(plan);
        when(stepMapper.selectListByPlan(any())).thenReturn(List.of(
                step("OPERATION", 1, "执行割接"), step("ROLLBACK", 1, "执行回退")));
        CutoverPlanFilePort.FileFact generated = fileFact();
        when(filePort.downloadDraft(1L, 8L, 70L, 80L)).thenReturn(generated);
        DirectPlatform platform = new DirectPlatform();
        CutoverPlanApplicationService service = new CutoverPlanApplicationService(taskMapper, planMapper,
                stepMapper, supportMapper, projectScope, mock(CutoverPlanSourcePort.class), filePort,
                new CutoverPlanContentCodec(), platform,
                Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC));

        DownloadCutoverPlanDraftResult result = service.downloadDraft(
                new DownloadCutoverPlanDraftCommand(1L, 8L, 50L, 3, "download-1", "corr-download-1"));

        assertThat(result.planRevisionId()).isEqualTo(80L);
        assertThat(result.planVersion()).isEqualTo(3);
        assertThat(result.fileArtifactFact()).isEqualTo(generated);
        assertThat(result.downloadedAt()).isEqualTo(Instant.parse("2026-09-01T01:00:00Z").toEpochMilli());
        assertThat(task.getVersion()).isEqualTo(4);
        assertThat(plan.getVersion()).isEqualTo(3);
        assertThat(platform.lastFacts.get().correlationId()).isEqualTo("corr-download-1");
        assertThat(platform.lastFacts.get().detailSnapshot()).contains("fileArtifactFact", "downloadedAt", "actorId");
    }

    @Test
    void submitsCompleteDraftAndAdvancesTaskToP5WithPendingApproval() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        CutoverSupportArrangementMapper supportMapper = mock(CutoverSupportArrangementMapper.class);
        CutoverTaskStageHistoryMapper historyMapper = mock(CutoverTaskStageHistoryMapper.class);
        CutoverProjectScopePort projectScope = mock(CutoverProjectScopePort.class);
        CutoverPlanSourcePort sourcePort = mock(CutoverPlanSourcePort.class);
        CutoverTaskDO task = task();
        CutoverPlanRevisionDO plan = simplePlan();
        List<CutoverPlanStepDO> steps = List.of(
                step("OPERATION", 1, "执行割接"), step("ROLLBACK", 1, "执行回退"));
        CutoverPlanSourcePort.SourceSnapshot snapshot = JsonUtils.parseObject(
                plan.getSourceSnapshot(), CutoverPlanSourcePort.SourceSnapshot.class);
        CutoverPlanSourcePort.SourceFacts facts = new CutoverPlanSourcePort.SourceFacts(snapshot, List.of());
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(planMapper.selectCurrent(any())).thenReturn(plan);
        when(stepMapper.selectListByPlan(any())).thenReturn(steps);
        when(projectScope.lockAndRevalidate(8L, 70L, "ACTION_EDIT", 30L))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(sourcePort.lockAndRevalidate(1L, 8L, facts)).thenReturn(facts);
        when(taskMapper.selectForUpdate(any())).thenReturn(task);
        when(planMapper.selectCurrentForUpdate(any())).thenReturn(plan);
        when(stepMapper.selectListByPlanForUpdate(any())).thenReturn(steps);
        when(supportMapper.selectListByPlanForUpdate(any())).thenReturn(List.of());
        when(planMapper.submitDraftIfMatch(any())).thenReturn(1);
        when(taskMapper.submitPlanIfMatch(any())).thenReturn(1);
        when(taskMapper.selectMaxStageHistorySequence(any())).thenReturn(2);
        when(historyMapper.insert(any(CutoverTaskStageHistoryDO.class))).thenReturn(1);
        DirectPlatform platform = new DirectPlatform();
        CutoverPlanApplicationService service = new CutoverPlanApplicationService(taskMapper, planMapper,
                stepMapper, supportMapper, projectScope, sourcePort, mock(CutoverPlanFilePort.class),
                new CutoverPlanContentCodec(), platform, new ControlledApprovalApi(), historyMapper,
                Clock.fixed(Instant.parse("2026-09-01T02:00:00Z"), ZoneOffset.UTC));

        SubmitCutoverPlanResult result = service.submit(
                new SubmitCutoverPlanCommand(1L, 8L, 50L, 4, 3, "submit-1", "corr-submit-1"));

        assertThat(result.taskStage()).isEqualTo("P5");
        assertThat(result.taskVersion()).isEqualTo(5);
        assertThat(result.planVersion()).isEqualTo(4);
        assertThat(result.approvalStatus()).isEqualTo("PENDING");
        assertThat(platform.lastFacts.get().correlationId()).isEqualTo("corr-submit-1");
        var planUpdate = org.mockito.ArgumentCaptor.forClass(CutoverPlanSubmitUpdate.class);
        verify(planMapper).submitDraftIfMatch(planUpdate.capture());
        assertThat(planUpdate.getValue().approvalInstanceId()).isEqualTo(70001L);
        assertThat(planUpdate.getValue().newVersion()).isEqualTo(4);
        var taskUpdate = org.mockito.ArgumentCaptor.forClass(CutoverTaskPlanSubmitUpdate.class);
        verify(taskMapper).submitPlanIfMatch(taskUpdate.capture());
        assertThat(taskUpdate.getValue().expectedVersion()).isEqualTo(4);
        var history = org.mockito.ArgumentCaptor.forClass(CutoverTaskStageHistoryDO.class);
        verify(historyMapper).insert(history.capture());
        assertThat(history.getValue()).extracting(CutoverTaskStageHistoryDO::getSequenceNo,
                        CutoverTaskStageHistoryDO::getFromStage, CutoverTaskStageHistoryDO::getToStage,
                        CutoverTaskStageHistoryDO::getTriggerType)
                .containsExactly(3, "P4", "P5", "P4_PLAN_SUBMITTED");
    }

    @Test
    void pausesApprovalAndReturnsTaskToP4WhenSubmittedSourceIsInvalidated() {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverTaskStageHistoryMapper historyMapper = mock(CutoverTaskStageHistoryMapper.class);
        CutoverTaskDO task = task();
        task.setCurrentStage("P5"); task.setTaskStatus("APPROVING"); task.setVersion(5);
        CutoverPlanRevisionDO plan = simplePlan();
        plan.setStatusCode("SUBMITTED"); plan.setSubmittedBy(8L);
        plan.setSubmittedAt(java.time.LocalDateTime.parse("2026-09-01T02:00:00"));
        plan.setApprovalInstanceId(70001L); plan.setApprovalVersion(0); plan.setVersion(4);
        when(taskMapper.selectForUpdate(any())).thenReturn(task);
        when(planMapper.selectCurrentForUpdate(any())).thenReturn(plan);
        when(planMapper.invalidateSubmittedIfMatch(any())).thenReturn(1);
        when(taskMapper.returnToPlanForSourceInvalidation(any())).thenReturn(1);
        when(taskMapper.selectMaxStageHistorySequence(any())).thenReturn(3);
        when(historyMapper.insert(any(CutoverTaskStageHistoryDO.class))).thenReturn(1);
        ControlledApprovalApi approval = new ControlledApprovalApi();
        approval.seed(plan);
        DirectPlatform platform = new DirectPlatform();
        CutoverPlanApplicationService service = new CutoverPlanApplicationService(taskMapper, planMapper,
                mock(CutoverPlanStepMapper.class), mock(CutoverSupportArrangementMapper.class),
                mock(CutoverProjectScopePort.class), mock(CutoverPlanSourcePort.class),
                mock(CutoverPlanFilePort.class), new CutoverPlanContentCodec(), platform, approval, historyMapper,
                Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneOffset.UTC));

        InvalidateCutoverPlanSourceResult result = service.invalidateSource(
                new InvalidateCutoverPlanSourceCommand(1L, 9L, 50L, 5, 4,
                        "invalidate-1", "corr-invalidate-1"));

        assertThat(result.taskStage()).isEqualTo("P4");
        assertThat(result.taskVersion()).isEqualTo(6);
        assertThat(result.planStatus()).isEqualTo("INVALIDATED");
        assertThat(result.planVersion()).isEqualTo(5);
        assertThat(result.approvalStatus()).isEqualTo("PAUSED_SOURCE_INVALIDATED");
        var planUpdate = org.mockito.ArgumentCaptor.forClass(CutoverPlanInvalidationUpdate.class);
        verify(planMapper).invalidateSubmittedIfMatch(planUpdate.capture());
        assertThat(planUpdate.getValue()).extracting(CutoverPlanInvalidationUpdate::expectedApprovalVersion,
                        CutoverPlanInvalidationUpdate::newApprovalVersion,
                        CutoverPlanInvalidationUpdate::reasonCode)
                .containsExactly(0, 1, "SOURCE_FACT_INVALIDATED");
        verify(taskMapper).returnToPlanForSourceInvalidation(
                new CutoverTaskSourceInvalidationUpdate(1L, 50L, 5));
        var history = org.mockito.ArgumentCaptor.forClass(CutoverTaskStageHistoryDO.class);
        verify(historyMapper).insert(history.capture());
        assertThat(history.getValue()).extracting(CutoverTaskStageHistoryDO::getFromStage,
                        CutoverTaskStageHistoryDO::getToStage, CutoverTaskStageHistoryDO::getTriggerType)
                .containsExactly("P5", "P4", "P5_SOURCE_INVALIDATED");
        assertThat(platform.lastFacts.get().correlationId()).isEqualTo("corr-invalidate-1");
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO();
        row.setId(50L); row.setTenantId(1L); row.setProjectId(70L); row.setTaskOrigin("NEW_PLATFORM");
        row.setOwnerUserId(8L); row.setCurrentStage("P4"); row.setTaskStatus("PLAN_DRAFTING");
        row.setVersion(4); return row;
    }

    private static CutoverPlanRevisionDO simplePlan() {
        CutoverPlanSourcePort.SourceSnapshot source = new CutoverPlanSourcePort.SourceSnapshot(1, 50L, 4,
                60L, 2, "D", null, null, 70L, 5, 30L,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(90L, "SN-90", 3L, "SWITCH", "TYPE-V1")),
                100L, "CFG-D", 1,
                List.of(new CutoverPlanSourcePort.TemplateSectionSnapshot("OPERATION", "操作", 1,
                                List.of("NETWORK_CUTOVER"), List.of("D"), true),
                        new CutoverPlanSourcePort.TemplateSectionSnapshot("ROLLBACK", "回退", 2,
                                List.of("NETWORK_CUTOVER"), List.of("D"), true)), List.of());
        CutoverPlanRevisionDO row = new CutoverPlanRevisionDO();
        row.setId(80L); row.setTenantId(1L); row.setCutoverTaskId(50L); row.setRevisionNo(1);
        row.setOriginCode("NEW_PLATFORM"); row.setEditModeCode("ONLINE_TEMPLATE_SIMPLE_D");
        row.setGradeCode("D"); row.setSourceSnapshot(JsonUtils.toJsonString(source));
        row.setContentSnapshot("{\"editMode\":\"ONLINE_TEMPLATE_SIMPLE_D\"}");
        row.setStatusCode("DRAFT"); row.setCurrentMarker(1); row.setVersion(3); return row;
    }

    private static CutoverPlanStepDO step(String section, int no, String content) {
        CutoverPlanStepDO row = new CutoverPlanStepDO();
        row.setSectionCode(section); row.setStepNo(no); row.setContent(content); return row;
    }

    private static CutoverPlanFilePort.FileFact fileFact() {
        return new CutoverPlanFilePort.FileFact(501L, 1, "cut-plan-draft-80",
                new CutoverPlanFilePort.FileFactVersion(1, 1, 1), 1L, "a".repeat(64));
    }

    private static final class ControlledApprovalApi implements CutoverApprovalFactApi {
        private CutoverApprovalFact fact;

        @Override
        public CutoverApprovalStartResult start(CutoverApprovalStartCommand command) {
            fact = new CutoverApprovalFact(70001L, 0, command.taskId(), command.planRevisionId(),
                    command.planRevisionNo(), ApprovalStatus.PENDING,
                    command.sourceSnapshotVersion(), null, null, null);
            return new CutoverApprovalStartResult(StartOutcome.STARTED, fact);
        }

        void seed(CutoverPlanRevisionDO plan) {
            CutoverPlanSourcePort.SourceSnapshot source = JsonUtils.parseObject(
                    plan.getSourceSnapshot(), CutoverPlanSourcePort.SourceSnapshot.class);
            fact = new CutoverApprovalFact(plan.getApprovalInstanceId(), plan.getApprovalVersion(),
                    plan.getCutoverTaskId(), plan.getId(), plan.getRevisionNo(), ApprovalStatus.PENDING,
                    source.snapshotVersion(), null, null, null);
        }

        @Override
        public CutoverApprovalCommandResult pauseForSourceInvalidation(CutoverApprovalPauseCommand command) {
            fact = new CutoverApprovalFact(fact.approvalInstanceId(), fact.approvalVersion() + 1,
                    fact.taskId(), fact.planRevisionId(), fact.planRevisionNo(),
                    ApprovalStatus.PAUSED_SOURCE_INVALIDATED, fact.sourceSnapshotVersion(), null, null, null);
            return new CutoverApprovalCommandResult(CommandOutcome.APPLIED, fact);
        }

        @Override public CutoverApprovalInspectResult inspect(CutoverApprovalFactQuery query) {
            throw new UnsupportedOperationException();
        }
        @Override public CutoverApprovalRevalidationResult lockAndRevalidate(CutoverApprovalRevalidationQuery query) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final AtomicReference<SuccessFacts> lastFacts = new AtomicReference<>();

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest,
                                              Class<T> responseType, Supplier<T> operation,
                                              Function<T, SuccessFacts> successFactsFactory) {
            T result = operation.get();
            SuccessFacts facts = successFactsFactory.apply(result);
            lastFacts.set(facts);
            return new ExecutionResult<>(Decision.NEW, result);
        }
    }
}
