package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalReviewItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNodeStatusUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskStageHistoryMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCalculator;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeSnapshotCodec;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalDecisionServiceTest {

    @Test
    void intermediateApproveActivatesNextNodeAndCreatesPendingNotification() {
        Fixture f = new Fixture(11L);
        f.givenRoot(1, "INITIATOR");
        CutoverApprovalNodeDO next = node(102L, 2, "SERVICE_MANAGER", "WAITING", 22L);
        doReturn(next).when(f.nodes).selectByInstanceAndNodeForUpdate(argThat(q -> q != null && q.nodeNo() == 2));

        var result = f.service.approve(new ApproveCutoverApprovalCommand(1L, 10L, 3, 0,
                yesItems(), null, "同意进入下一节点", "key-1", "corr-1"));

        assertThat(result.approvalStatus()).isEqualTo("PENDING");
        assertThat(result.currentNodeNo()).isEqualTo(2);
        verify(f.tasks, never()).transitionFromApprovalIfMatch(any());
        ArgumentCaptor<ApprovalNodeStatusUpdate> updates = ArgumentCaptor.forClass(ApprovalNodeStatusUpdate.class);
        verify(f.nodes, times(2)).updateStatusIfMatch(updates.capture());
        assertThat(updates.getAllValues()).extracting(ApprovalNodeStatusUpdate::newStatusCode)
                .containsExactly("APPROVED", "PENDING");
        ArgumentCaptor<CutoverApprovalNotificationDO> notifications =
                ArgumentCaptor.forClass(CutoverApprovalNotificationDO.class);
        verify(f.notifications, times(4)).insert(notifications.capture());
        assertThat(notifications.getAllValues()).extracting(CutoverApprovalNotificationDO::getChannelCode)
                .containsExactly("IN_PLATFORM", "SMS", "EMAIL", "DINGTALK");
        assertThat(notifications.getAllValues()).allSatisfy(row -> {
            assertThat(row.getRecipientUserId()).isEqualTo(22L);
            assertThat(row.getCorrelationId()).isEqualTo("corr-1");
            assertThat(row.getStatusCode()).isEqualTo("PENDING");
            assertThat(row.getNextRetryAt()).isNull();
        });
    }

    @Test
    void replaysIntermediateDecisionWithoutReenteringCurrentNodeGuard() {
        Fixture f = new Fixture(11L);
        f.givenRoot(1, "INITIATOR");
        CutoverApprovalNodeDO next = node(102L, 2, "SERVICE_MANAGER", "WAITING", 22L);
        doReturn(next).when(f.nodes).selectByInstanceAndNodeForUpdate(argThat(q -> q != null && q.nodeNo() == 2));
        var command = new ApproveCutoverApprovalCommand(1L, 10L, 3, 0,
                yesItems(), null, "同意进入下一节点", "key-replay", "corr-replay");

        var first = f.service.approve(command);
        var replay = f.service.approve(command);

        assertThat(first.decidedNodeNo()).isEqualTo(1);
        assertThat(first.currentNodeNo()).isEqualTo(2);
        assertThat(replay).isEqualTo(first);
        verify(f.instances, times(1)).selectCurrentByTask(any());
        verify(f.reviews, times(5)).insert(any(CutoverApprovalReviewItemDO.class));
    }

    @Test
    void activatingReassignedWaitingNodeUsesItsCurrentRecipientAndCommittedVersion() {
        Fixture f = new Fixture(11L);
        f.givenRoot(1, "INITIATOR");
        CutoverApprovalNodeDO next = node(102L, 2, "SERVICE_MANAGER", "WAITING", 44L);
        next.setVersion(1);
        doReturn(next).when(f.nodes).selectByInstanceAndNodeForUpdate(argThat(q -> q != null && q.nodeNo() == 2));

        f.service.approve(new ApproveCutoverApprovalCommand(1L, 10L, 3, 0,
                yesItems(), null, "同意进入已改派节点", "key-reassigned-next", "corr-reassigned-next"));

        verify(f.notifications).insert(argThat((CutoverApprovalNotificationDO row) ->
                row.getRecipientUserId().equals(44L)
                        && "corr-reassigned-next".equals(row.getCorrelationId())
                        && "CUT_APPROVAL_EXT:100:2:2:SMS".equals(row.getDeliveryKey())));
    }

    @Test
    void rejectReturnsTaskToP4AndCancelsFutureNodes() {
        Fixture f = new Fixture(11L);
        f.givenRoot(1, "INITIATOR");
        when(f.nodes.selectList(any())).thenReturn(List.of(node(102L, 2, "SERVICE_MANAGER", "WAITING", 22L)));
        when(f.tasks.transitionFromApprovalIfMatch(any())).thenReturn(1);
        when(f.tasks.selectMaxStageHistorySequence(any())).thenReturn(4);

        var result = f.service.reject(new RejectCutoverApprovalCommand(1L, 10L, 3, 0,
                noItems(), null, "方案需修改", "key-2", "corr-2"));

        assertThat(result.approvalStatus()).isEqualTo("REJECTED");
        assertThat(result.taskStage()).isEqualTo("P4");
        verify(f.history).insert(argThat((CutoverTaskStageHistoryDO row) -> "P5_APPROVAL_REJECTED".equals(row.getTriggerType())
                && "corr-2".equals(row.getCorrelationId())));
        verify(f.notifications, never()).insert(any(CutoverApprovalNotificationDO.class));
    }

    @Test
    void finalApproveMovesTaskToP6AndPublishesOneBusinessEvent() {
        Fixture f = new Fixture(22L);
        f.givenRoot(2, "SERVICE_MANAGER");
        doReturn(null).when(f.nodes).selectByInstanceAndNodeForUpdate(argThat(q -> q != null && q.nodeNo() == 3));
        when(f.tasks.transitionFromApprovalIfMatch(any())).thenReturn(1);
        when(f.tasks.selectMaxStageHistorySequence(any())).thenReturn(1);
        var manager = new ProjectCutoverServiceManagerPort.ServiceManagerFact(
                ProjectCutoverServiceManagerPort.Outcome.FOUND, 1L, 20L, 22L,
                "SERVICE_MANAGER_L1", 3, 4L, java.time.LocalDateTime.now(f.clock));
        when(f.managers.inspectCurrent(anyLong(), anyLong(), any())).thenReturn(manager);
        when(f.managers.lockAndRevalidate(manager)).thenReturn(new ProjectCutoverServiceManagerPort.ServiceManagerRevalidation(
                ProjectCutoverServiceManagerPort.Revalidation.VALID, manager));

        var result = f.service.approve(new ApproveCutoverApprovalCommand(1L, 10L, 3, 0,
                yesItems(), new AssessmentReviewInput("CONFIRMED", null), "全部通过", "key-3", "corr-3"));

        assertThat(result.approvalStatus()).isEqualTo("APPROVED");
        assertThat(result.taskStage()).isEqualTo("P6");
        assertThat(f.successFacts.businessEvents()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("CutoverApproved");
            assertThat(event.eventPayload()).contains("\"planRevisionId\":200", "\"sourceSnapshotVersion\":1");
        });
        assertThat(f.successFacts.correlationId()).isEqualTo("corr-3");
        verify(f.history).insert(argThat((CutoverTaskStageHistoryDO row) -> "P5_APPROVAL_APPROVED".equals(row.getTriggerType())));
    }

    @Test
    void serviceManagerCanRejectNoReviewWhileConfirmingAssessment() {
        Fixture f = new Fixture(22L);
        f.givenRoot(2, "SERVICE_MANAGER");
        when(f.nodes.selectList(any())).thenReturn(List.of());
        when(f.tasks.transitionFromApprovalIfMatch(any())).thenReturn(1);

        var result = f.service.reject(new RejectCutoverApprovalCommand(1L, 10L, 3, 0,
                noItems(), new AssessmentReviewInput("CONFIRMED", null), "准备项需完善", "key-4", "corr-4"));

        assertThat(result.approvalStatus()).isEqualTo("REJECTED");
        assertThat(result.taskStage()).isEqualTo("P4");
    }

    @Test
    void leadTimeDisplayFactDoesNotChangeTheSameApprovalDecision() {
        Fixture late = new Fixture(11L);
        Fixture onTime = new Fixture(11L);
        late.givenRoot(1, "INITIATOR");
        onTime.givenRoot(1, "INITIATOR");
        late.setLeadTime(LocalDateTime.of(2026, 9, 4, 8, 0));
        onTime.setLeadTime(LocalDateTime.of(2026, 9, 5, 8, 0));
        doReturn(node(102L, 2, "SERVICE_MANAGER", "WAITING", 22L)).when(late.nodes)
                .selectByInstanceAndNodeForUpdate(argThat(q -> q != null && q.nodeNo() == 2));
        doReturn(node(102L, 2, "SERVICE_MANAGER", "WAITING", 22L)).when(onTime.nodes)
                .selectByInstanceAndNodeForUpdate(argThat(q -> q != null && q.nodeNo() == 2));
        var command = new ApproveCutoverApprovalCommand(1L, 10L, 3, 0,
                yesItems(), null, "同意进入下一节点", "same-decision", "same-correlation");

        var lateResult = late.service.approve(command);
        var onTimeResult = onTime.service.approve(command);

        assertThat(lateResult.approvalStatus()).isEqualTo(onTimeResult.approvalStatus()).isEqualTo("PENDING");
        assertThat(lateResult.currentNodeNo()).isEqualTo(onTimeResult.currentNodeNo()).isEqualTo(2);
        assertThat(lateResult.taskStage()).isEqualTo(onTimeResult.taskStage()).isEqualTo("P5");
    }

    @Test
    void exposesStructuredTaskVersionAndReviewReasonCodes() {
        Fixture f = new Fixture(11L);
        f.givenRoot(1, "INITIATOR");

        assertThatThrownBy(() -> f.service.approve(new ApproveCutoverApprovalCommand(1L, 10L, 2, 0,
                yesItems(), null, "同意", "key-version", "corr-version")))
                .isInstanceOfSatisfying(CutoverApprovalApplicationException.class, ex -> {
                    assertThat(ex.reasonCode()).isEqualTo("TASK_VERSION_STALE");
                    assertThat(ex.currentTaskVersion()).isEqualTo(3);
                });
        assertThatThrownBy(() -> f.service.approve(new ApproveCutoverApprovalCommand(1L, 10L, 3, 0,
                List.of(), null, "同意", "key-items", "corr-items")))
                .isInstanceOfSatisfying(CutoverApprovalApplicationException.class, ex ->
                        assertThat(ex.reasonCode()).isEqualTo("REVIEW_ITEMS_INCOMPLETE"));
    }

    private static List<ReviewItemInput> yesItems() {
        return List.of(new ReviewItemInput("PREPARATION", "YES", null),
                new ReviewItemInput("BUSINESS_TEST", "YES", null),
                new ReviewItemInput("EXECUTION", "YES", null),
                new ReviewItemInput("ROLLBACK", "YES", null),
                new ReviewItemInput("OTHER", "YES", null));
    }

    private static List<ReviewItemInput> noItems() {
        return List.of(new ReviewItemInput("PREPARATION", "NO", "准备不足"),
                new ReviewItemInput("BUSINESS_TEST", "YES", null),
                new ReviewItemInput("EXECUTION", "YES", null),
                new ReviewItemInput("ROLLBACK", "YES", null),
                new ReviewItemInput("OTHER", "YES", null));
    }

    private static CutoverApprovalNodeDO node(long id, int no, String code, String status, long approver) {
        CutoverApprovalNodeDO row = new CutoverApprovalNodeDO();
        row.setId(id); row.setTenantId(1L); row.setApprovalInstanceId(100L); row.setNodeNo(no);
        row.setNodeCode(code); row.setStatusCode(status); row.setCurrentApproverUserId(approver);
        row.setProjectScopeVersion(7L); row.setVersion(0);
        return row;
    }

    private static final class Fixture {
        final CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        final CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        final CutoverApprovalNotificationMapper notifications = mock(CutoverApprovalNotificationMapper.class);
        final CutoverApprovalReviewItemMapper reviews = mock(CutoverApprovalReviewItemMapper.class);
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final CutoverTaskStageHistoryMapper history = mock(CutoverTaskStageHistoryMapper.class);
        final ProjectCutoverServiceManagerPort managers = mock(ProjectCutoverServiceManagerPort.class);
        final CutoverApprovalRoleCandidatePort candidates = mock(CutoverApprovalRoleCandidatePort.class);
        final CutoverApprovalProjectScopePort scopes = mock(CutoverApprovalProjectScopePort.class);
        final PlatformCommandExecutionApi platform = mock(PlatformCommandExecutionApi.class);
        final Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
        final long actor;
        PlatformCommandExecutionApi.SuccessFacts successFacts;
        Object completedResponse;
        CutoverApprovalInstanceDO root;
        final CutoverApprovalApplicationService service;

        Fixture(long actor) {
            this.actor = actor;
            when(reviews.insert(any(CutoverApprovalReviewItemDO.class))).thenReturn(1);
            when(nodes.updateStatusIfMatch(any())).thenReturn(1);
            when(instances.updateStateIfMatch(any())).thenReturn(1);
            when(notifications.insert(any(CutoverApprovalNotificationDO.class))).thenReturn(1);
            when(history.insert(any(CutoverTaskStageHistoryDO.class))).thenReturn(1);
            doAnswer(invocation -> {
                if (completedResponse != null) {
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, completedResponse);
                }
                @SuppressWarnings("unchecked") java.util.function.Supplier<Object> operation = invocation.getArgument(3);
                @SuppressWarnings("unchecked") java.util.function.Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
                Object response = operation.get(); completedResponse = response; successFacts = facts.apply(response);
                return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, response);
            }).when(platform).execute(any(), anyString(), any(), any(), any());
            service = new CutoverApprovalApplicationService(null, instances, nodes, notifications, reviews, tasks,
                    history, managers, candidates, scopes, platform, () -> actor, clock);
        }

        void givenRoot(int currentNo, String nodeCode) {
            CutoverApprovalInstanceDO instance = new CutoverApprovalInstanceDO();
            instance.setId(100L); instance.setTenantId(1L); instance.setTaskId(10L); instance.setProjectId(20L);
            instance.setPlanRevisionId(200L); instance.setSourceSnapshotVersion(1); instance.setStatusCode("PENDING");
            instance.setCurrentNodeNo(currentNo); instance.setVersion(0);
            root = instance;
            when(instances.selectByIdForUpdate(any())).thenReturn(instance);
            when(instances.selectCurrentByTask(any())).thenReturn(instance);
            CutoverTaskDO task = new CutoverTaskDO(); task.setId(10L); task.setTenantId(1L); task.setVersion(3);
            task.setCurrentStage("P5"); task.setTaskStatus("APPROVING");
            when(tasks.selectForUpdate(any())).thenReturn(task);
            CutoverApprovalNodeDO current = node(100L + currentNo, currentNo, nodeCode, "PENDING", actor);
            if ("SERVICE_MANAGER".equals(nodeCode)) {
                var manager = new ProjectCutoverServiceManagerPort.ServiceManagerFact(
                        ProjectCutoverServiceManagerPort.Outcome.FOUND, 1L, 20L, actor,
                        "SERVICE_MANAGER_L1", 3, 4L, java.time.LocalDateTime.now(clock));
                current.setCandidateFactSnapshot(JsonUtils.toJsonString(manager));
                when(managers.lockAndRevalidate(manager)).thenReturn(
                        new ProjectCutoverServiceManagerPort.ServiceManagerRevalidation(
                                ProjectCutoverServiceManagerPort.Revalidation.VALID, manager));
            }
            doReturn(current).when(nodes)
                    .selectByInstanceAndNodeForUpdate(argThat(q -> q != null && q.nodeNo() == currentNo));
            var scope = new CutoverApprovalProjectScopePort.ProjectScopeFact(1L, 20L, actor,
                    "ACTION_EDIT", true, 7L);
            when(scopes.inspect(1L, 20L, actor, "ACTION_EDIT")).thenReturn(scope);
            when(scopes.lockAndRevalidate(scope)).thenReturn(new CutoverApprovalProjectScopePort.ProjectScopeRevalidation(
                    CutoverApprovalProjectScopePort.Revalidation.VALID, scope));
        }

        void setLeadTime(LocalDateTime scheduledTime) {
            root.setLeadTimeEnabled(true);
            root.setLeadTimeSnapshot(new CutoverLeadTimeSnapshotCodec().encode(
                    new CutoverLeadTimeCalculator().calculate("A", "VERSION_UPGRADE", scheduledTime,
                            LocalDateTime.of(2026, 9, 3, 18, 0))));
        }
    }
}
