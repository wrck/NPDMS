package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.ControlledCutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.ApprovalStatus;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFact;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalFactQuery;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalPauseCommand;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.CutoverApprovalStartCommand;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanReplacementUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverSupportContactUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.PatchApprovedContactCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.ReviseCutoverPlanCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.SubmitCutoverPlanCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.CutoverPlanCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.PatchApprovedContactResult;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.SubmitCutoverPlanResult;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverTaskRules;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverPlanRevisionLifecycleTest {

    @Test
    void rejectedApprovalCreatesNextDraftWithoutOverwritingSource() {
        Fixture fixture = fixture("SUBMITTED", 1, ApprovalStatus.REJECTED, "APPROVAL_REJECTED");

        CutoverPlanCommandResult result = fixture.service.revise(new ReviseCutoverPlanCommand(
                1L, 8L, 50L, 6, 601L, "APPROVAL_REJECTED", "revise-rejected", "corr-rejected"));

        assertThat(result.revisionNo()).isEqualTo(2);
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(fixture.derived.get().getSourcePlanRevisionId()).isEqualTo(601L);
        assertThat(fixture.derived.get().getRevisionReasonCode()).isEqualTo("APPROVAL_REJECTED");
        assertThat(fixture.derived.get().getContentSnapshot()).isEqualTo(fixture.source.getContentSnapshot());
        assertThat(JsonUtils.parseObject(fixture.derived.get().getSourceSnapshot(),
                CutoverPlanSourcePort.SourceSnapshot.class).taskVersion()).isEqualTo(6);
        ArgumentCaptor<CutoverPlanReplacementUpdate> replacement = ArgumentCaptor.forClass(
                CutoverPlanReplacementUpdate.class);
        verify(fixture.planMapper).replaceSubmittedIfMatch(replacement.capture());
        assertThat(replacement.getValue().planRevisionId()).isEqualTo(601L);
        assertThat(fixture.copiedSteps).extracting(CutoverPlanStepDO::getContent)
                .containsExactly("执行割接", "执行回退");
        assertThat(fixture.copiedSupport).isEmpty();

        CutoverPlanRevisionDO derived = fixture.derived.get();
        when(fixture.planMapper.selectCurrent(any())).thenReturn(derived);
        when(fixture.planMapper.selectCurrentForUpdate(any())).thenReturn(derived);
        when(fixture.planMapper.submitDraftIfMatch(any())).thenReturn(1);
        when(fixture.taskMapper.submitPlanIfMatch(any())).thenReturn(1);
        when(fixture.taskMapper.selectMaxStageHistorySequence(any())).thenReturn(1);
        when(fixture.historyMapper.insert(any(CutoverTaskStageHistoryDO.class))).thenReturn(1);
        when(fixture.stepMapper.selectListByPlan(any())).thenReturn(fixture.copiedSteps);
        when(fixture.supportMapper.selectListByPlan(any())).thenReturn(fixture.copiedSupport);
        when(fixture.stepMapper.selectListByPlanForUpdate(any())).thenReturn(fixture.copiedSteps);
        when(fixture.supportMapper.selectListByPlanForUpdate(any())).thenReturn(fixture.copiedSupport);

        SubmitCutoverPlanResult submitted = fixture.service.submit(new SubmitCutoverPlanCommand(
                1L, 8L, 50L, 6, 0, "submit-replacement", "corr-replacement"));

        assertThat(submitted.approvalStatus()).isEqualTo("PENDING");
        CutoverApprovalFact oldApproval = fixture.approval.inspect(new CutoverApprovalFactQuery(
                1L, 50L, 601L)).fact();
        assertThat(oldApproval.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(oldApproval.replacementApprovalInstanceId()).isEqualTo(submitted.approvalInstanceId());
    }

    @Test
    void invalidatedSourceCreatesReplacementDraftWithoutRewritingOldRevision() {
        Fixture fixture = fixture("INVALIDATED", null, ApprovalStatus.PAUSED_SOURCE_INVALIDATED,
                "SOURCE_REPLACED");

        CutoverPlanCommandResult result = fixture.service.revise(new ReviseCutoverPlanCommand(
                1L, 8L, 50L, 6, 601L, "SOURCE_REPLACED", "revise-source", "corr-source"));

        assertThat(result.revisionNo()).isEqualTo(2);
        assertThat(fixture.derived.get().getRevisionReasonCode()).isEqualTo("SOURCE_REPLACED");
        verify(fixture.planMapper, never()).replaceSubmittedIfMatch(any());
        verify(fixture.sourcePort).lockAndRevalidate(eq(1L), eq(8L), any());
    }

    @Test
    void approvedContactPatchAdvancesOnlyRootVersionAndContactFields() {
        Fixture fixture = fixture("SUBMITTED", 1, ApprovalStatus.APPROVED, null);
        fixture.task.setCurrentStage("P6");
        fixture.task.setTaskStatus("CLOSURE_IN_PROGRESS");
        LocalDateTime arrival = LocalDateTime.of(2026, 9, 2, 9, 0);

        PatchApprovedContactResult result = fixture.service.patchApprovedContact(new PatchApprovedContactCommand(
                1L, 8L, 50L, 801L, 2, "李工", "13800000000", arrival,
                "patch-contact", "corr-contact"));

        assertThat(result.planVersion()).isEqualTo(3);
        assertThat(result.reasonCode()).isEqualTo("APPROVED_CONTACT_CHANGED");
        assertThat(result.before().personName()).isEqualTo("张工");
        assertThat(result.after().personName()).isEqualTo("李工");
        ArgumentCaptor<CutoverSupportContactUpdate> update = ArgumentCaptor.forClass(CutoverSupportContactUpdate.class);
        verify(fixture.supportMapper).updateApprovedContactIfMatch(update.capture());
        assertThat(update.getValue().expectedVersion()).isZero();
        assertThat(update.getValue().personName()).isEqualTo("李工");
        assertThat(fixture.platform.facts).singleElement().satisfies(fact -> {
            assertThat(fact.correlationId()).isEqualTo("corr-contact");
            assertThat(fact.detailSnapshot()).contains("张工", "李工", "13800000000",
                    "APPROVED_CONTACT_CHANGED");
        });
        assertThat(fixture.support.getRoleCode()).isEqualTo("CUSTOMER");
        assertThat(fixture.support.getDutyDescription()).isEqualTo("现场协调");
    }

    private static Fixture fixture(String status, Integer currentMarker, ApprovalStatus approvalStatus,
                                   String reason) {
        CutoverTaskMapper taskMapper = mock(CutoverTaskMapper.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        CutoverSupportArrangementMapper supportMapper = mock(CutoverSupportArrangementMapper.class);
        CutoverTaskStageHistoryMapper historyMapper = mock(CutoverTaskStageHistoryMapper.class);
        CutoverProjectScopePort project = mock(CutoverProjectScopePort.class);
        CutoverPlanSourcePort sourcePort = mock(CutoverPlanSourcePort.class);
        CutoverPlanFilePort filePort = mock(CutoverPlanFilePort.class);
        CutoverTaskDO task = task();
        when(taskMapper.selectById(50L)).thenReturn(task);
        when(taskMapper.selectForUpdate(any())).thenReturn(task);
        when(project.inspect(8L, 70L, "ACTION_EDIT"))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        when(project.lockAndRevalidate(8L, 70L, "ACTION_EDIT", 30L))
                .thenReturn(new CutoverProjectScopePort.ProjectScopeFact(70L, 30L, true));
        CutoverPlanSourcePort.SourceFacts currentFacts = sourceFacts(6);
        when(sourcePort.inspect(1L, 8L, 50L)).thenReturn(currentFacts);
        when(sourcePort.lockAndRevalidate(eq(1L), eq(8L), any())).thenReturn(currentFacts);

        ControlledCutoverApprovalFactApi approval = new ControlledCutoverApprovalFactApi();
        CutoverPlanSourcePort.SourceFacts frozenFacts = sourceFacts(4);
        CutoverPlanRevisionDO source = sourcePlan(status, currentMarker, frozenFacts.snapshot());
        CutoverApprovalFact started = approval.start(new CutoverApprovalStartCommand(1L, 50L, 4,
                601L, 1, "D", 100L, 2, null, null, 1,
                java.time.LocalDateTime.of(2026, 9, 3, 18, 0), null, "approval-start", "approval-corr")).fact();
        CutoverApprovalFact decided = switch (approvalStatus) {
            case REJECTED -> approval.reject(started.approvalInstanceId(), 1000L, "补充回退步骤");
            case APPROVED -> approval.approve(started.approvalInstanceId(), 1000L);
            case PAUSED_SOURCE_INVALIDATED -> approval.pauseForSourceInvalidation(new CutoverApprovalPauseCommand(
                    1L, started.approvalInstanceId(), started.approvalVersion(), 601L, 1,
                    "SOURCE_FACT_INVALIDATED", "pause-source", "pause-corr")).fact();
            default -> started;
        };
        source.setApprovalInstanceId(decided.approvalInstanceId());
        source.setApprovalVersion("INVALIDATED".equals(status) ? decided.approvalVersion() : 0);
        when(planMapper.selectById(601L)).thenReturn(source);
        when(planMapper.selectByIdForUpdate(any())).thenReturn(source);
        when(planMapper.selectCurrentForUpdate(any())).thenReturn(currentMarker == null ? null : source);
        when(planMapper.selectCurrent(any())).thenReturn(source);
        when(planMapper.selectListDirectSuccessors(any())).thenReturn(List.of());
        when(planMapper.selectMaxRevisionNo(any())).thenReturn(1);
        when(planMapper.replaceSubmittedIfMatch(any())).thenReturn(1);
        when(planMapper.advanceApprovedVersionIfMatch(any())).thenReturn(1);
        AtomicReference<CutoverPlanRevisionDO> derived = new AtomicReference<>();
        when(planMapper.insert(any(CutoverPlanRevisionDO.class))).thenAnswer(invocation -> {
            derived.set(invocation.getArgument(0));
            return 1;
        });

        CutoverPlanStepDO step = new CutoverPlanStepDO();
        step.setId(701L); step.setTenantId(1L); step.setPlanRevisionId(601L);
        step.setSectionCode("OPERATION"); step.setStepNo(1); step.setContent("执行割接"); step.setVersion(0);
        CutoverPlanStepDO rollback = new CutoverPlanStepDO();
        rollback.setId(702L); rollback.setTenantId(1L); rollback.setPlanRevisionId(601L);
        rollback.setSectionCode("ROLLBACK"); rollback.setStepNo(1); rollback.setContent("执行回退"); rollback.setVersion(0);
        CutoverSupportArrangementDO support = new CutoverSupportArrangementDO();
        support.setId(801L); support.setTenantId(1L); support.setPlanRevisionId(601L);
        support.setRoleCode("CUSTOMER"); support.setPersonName("张工"); support.setPhone("13900000000");
        support.setDutyDescription("现场协调"); support.setArrivalTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        support.setVersion(0);
        when(stepMapper.selectListByPlanForUpdate(any())).thenReturn(List.of(step, rollback));
        when(supportMapper.selectListByPlanForUpdate(any())).thenReturn(List.of(support));
        List<CutoverPlanStepDO> copiedSteps = new ArrayList<>();
        List<CutoverSupportArrangementDO> copiedSupport = new ArrayList<>();
        when(stepMapper.insert(any(CutoverPlanStepDO.class))).thenAnswer(invocation -> {
            copiedSteps.add(invocation.getArgument(0)); return 1;
        });
        when(supportMapper.insert(any(CutoverSupportArrangementDO.class))).thenAnswer(invocation -> {
            copiedSupport.add(invocation.getArgument(0)); return 1;
        });
        when(supportMapper.updateApprovedContactIfMatch(any())).thenReturn(1);
        when(taskMapper.submitPlanIfMatch(any())).thenReturn(1);
        when(historyMapper.insert(any(CutoverTaskStageHistoryDO.class))).thenReturn(1);
        DirectPlatform platform = new DirectPlatform();
        CutoverPlanApplicationService service = new CutoverPlanApplicationService(taskMapper, planMapper, stepMapper,
                supportMapper, project, sourcePort, filePort, new CutoverPlanContentCodec(), platform,
                approval, historyMapper, Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
        return new Fixture(service, task, source, support, sourcePort, taskMapper, planMapper, stepMapper,
                supportMapper, historyMapper, approval, derived, copiedSteps, copiedSupport, platform);
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(50L); task.setTenantId(1L); task.setProjectId(70L); task.setOwnerUserId(8L);
        task.setTaskOrigin(CutoverTaskRules.ORIGIN_NEW_PLATFORM); task.setCurrentStage("P4");
        task.setTaskStatus("PLAN_DRAFTING"); task.setVersion(6);
        return task;
    }

    private static CutoverPlanRevisionDO sourcePlan(String status, Integer currentMarker,
                                                     CutoverPlanSourcePort.SourceSnapshot snapshot) {
        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO();
        plan.setId(601L); plan.setTenantId(1L); plan.setCutoverTaskId(50L); plan.setRevisionNo(1);
        plan.setOriginCode("NEW_PLATFORM"); plan.setEditModeCode("ONLINE_TEMPLATE_SIMPLE_D");
        plan.setGradeCode("D"); plan.setAssessmentId(100L); plan.setAssessmentVersion(2);
        plan.setConfigurationRevisionId(401L); plan.setConfigurationCode("CFG-1"); plan.setConfigurationRevisionNo(1);
        plan.setTemplateSectionSnapshot(JsonUtils.toJsonString(snapshot.templateSections()));
        plan.setSourceSnapshot(JsonUtils.toJsonString(snapshot));
        plan.setContentSnapshot("{\"editMode\":\"ONLINE_TEMPLATE_SIMPLE_D\"}");
        plan.setStatusCode(status); plan.setCurrentMarker(currentMarker); plan.setVersion(2);
        return plan;
    }

    private static CutoverPlanSourcePort.SourceFacts sourceFacts(int taskVersion) {
        List<CutoverPlanSourcePort.TemplateSectionSnapshot> templates = List.of(
                new CutoverPlanSourcePort.TemplateSectionSnapshot("OPERATION", "操作", 1,
                        List.of("NETWORK_CUTOVER"), List.of("D"), true),
                new CutoverPlanSourcePort.TemplateSectionSnapshot("ROLLBACK", "回退", 2,
                        List.of("NETWORK_CUTOVER"), List.of("D"), true));
        CutoverPlanSourcePort.SourceSnapshot snapshot = new CutoverPlanSourcePort.SourceSnapshot(1, 50L,
                taskVersion, 100L, 2, "D", null, null, 70L, 6, 30L,
                List.of(new CutoverPlanSourcePort.DeviceSnapshot(301L, "SN-1", 9L, "ROUTER", "type-v1")),
                401L, "CFG-1", 1, templates, List.of());
        return new CutoverPlanSourcePort.SourceFacts(snapshot, List.of());
    }

    private record Fixture(CutoverPlanApplicationService service, CutoverTaskDO task,
                           CutoverPlanRevisionDO source, CutoverSupportArrangementDO support,
                           CutoverPlanSourcePort sourcePort, CutoverTaskMapper taskMapper,
                           CutoverPlanRevisionMapper planMapper, CutoverPlanStepMapper stepMapper,
                           CutoverSupportArrangementMapper supportMapper,
                           CutoverTaskStageHistoryMapper historyMapper,
                           ControlledCutoverApprovalFactApi approval,
                           AtomicReference<CutoverPlanRevisionDO> derived,
                           List<CutoverPlanStepDO> copiedSteps,
                           List<CutoverSupportArrangementDO> copiedSupport, DirectPlatform platform) {
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final Map<String, Object> responses = new HashMap<>();
        private final List<SuccessFacts> facts = new ArrayList<>();

        @Override
        public <T> ExecutionResult<T> execute(IdempotencyScope scope, String requestDigest, Class<T> responseType,
                                              Supplier<T> operation, Function<T, SuccessFacts> successFactsFactory) {
            String key = scope.scopeCode() + ":" + scope.key();
            if (responses.containsKey(key)) {
                return new ExecutionResult<>(Decision.REPLAY_COMPLETED, responseType.cast(responses.get(key)));
            }
            T response = operation.get();
            facts.add(successFactsFactory.apply(response));
            responses.put(key, response);
            return new ExecutionResult<>(Decision.NEW, response);
        }
    }
}
