package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
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
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalStartServiceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void startsGradeARouteWithRealInitiatorAndOnePendingNotificationThenReplays(boolean replacesWithdrawn) {
        CutoverApprovalSourceAssembler assembler = mock(CutoverApprovalSourceAssembler.class);
        CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        CutoverApprovalNotificationMapper notifications = mock(CutoverApprovalNotificationMapper.class);
        CutoverTaskDO task = new CutoverTaskDO(); task.setId(100L); task.setProjectId(10L);
        task.setCutoverType("VERSION_UPGRADE");
        task.setScheduledTime(LocalDateTime.of(2026, 9, 5, 8, 0));
        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO();
        LocalDateTime planSubmittedAt = LocalDateTime.of(2026, 9, 3, 18, 0);
        when(assembler.lockAndAssemble(any())).thenReturn(
                new CutoverApprovalSourceAssembler.LockedSource(task, null, null, plan, CutoverApprovalControlledPorts.approvalSource("A")));
        when(instances.insert(any(CutoverApprovalInstanceDO.class))).thenReturn(1);
        Long previousId = replacesWithdrawn ? 800L : null;
        var previous = new CutoverApprovalInstanceDO();
        previous.setId(previousId); previous.setTenantId(1L); previous.setTaskId(100L);
        previous.setStatusCode("WITHDRAWN"); previous.setVersion(2L);
        previous.setWithdrawUserId(202L); previous.setWithdrawReason("修订调研结果");
        when(instances.selectByIdForUpdate(any())).thenReturn(previous);
        when(instances.updateStateIfMatch(any())).thenReturn(1);
        when(nodes.insert(any(CutoverApprovalNodeDO.class))).thenReturn(1);
        when(notifications.insert(any(CutoverApprovalNotificationDO.class))).thenReturn(1);
        DirectPlatform platform = new DirectPlatform();
        CutoverApprovalApplicationService service = new CutoverApprovalApplicationService(assembler,
                instances,
                nodes,
                notifications,
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReassignmentMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper.class),
                CutoverApprovalControlledPorts.serviceManager(301L),
                CutoverApprovalControlledPorts.roleCandidates(),
                CutoverApprovalControlledPorts.projectScope(202L),
                platform,
                () -> 202L,
                Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverWorkflowCoordinator.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "storage", mock(CutoverApprovalStorage.class));
        CutoverApprovalStartCommand command = new CutoverApprovalStartCommand(1L, 100L, 5L, 900L, 1,
                "A", 600L, 2L, 700L, 3L, 1, planSubmittedAt, previousId, "start-1", "corr-1");

        CutoverApprovalStartResult started = service.start(command);
        CutoverApprovalStartCommand sameBusinessNewCorrelation = new CutoverApprovalStartCommand(1L, 100L, 5L,
                900L, 1, "A", 600L, 2L, 700L, 3L, 1, planSubmittedAt, previousId, "start-1", "corr-2");
        CutoverApprovalStartResult replayed = service.start(sameBusinessNewCorrelation);
        CutoverApprovalStartCommand changed = new CutoverApprovalStartCommand(1L, 100L, 6L, 900L, 1,
                "A", 600L, 2L, 700L, 3L, 1, planSubmittedAt, previousId, "start-1", "corr-1");
        CutoverApprovalApplicationException conflict = assertThrows(CutoverApprovalApplicationException.class,
                () -> service.start(changed));

        assertEquals(StartOutcome.STARTED, started.outcome());
        assertEquals(StartOutcome.REPLAYED, replayed.outcome());
        assertEquals(started.fact().approvalInstanceId(), replayed.fact().approvalInstanceId());
        ArgumentCaptor<CutoverApprovalNodeDO> nodeCaptor = ArgumentCaptor.forClass(CutoverApprovalNodeDO.class);
        verify(nodes, times(4)).insert(nodeCaptor.capture());
        assertEquals(List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE", "RND"),
                nodeCaptor.getAllValues().stream().map(CutoverApprovalNodeDO::getNodeCode).toList());
        assertEquals("PENDING", nodeCaptor.getAllValues().getFirst().getStatusCode());
        assertEquals(202L, nodeCaptor.getAllValues().getFirst().getCurrentApproverUserId());
        ArgumentCaptor<CutoverApprovalInstanceDO> instanceCaptor =
                ArgumentCaptor.forClass(CutoverApprovalInstanceDO.class);
        verify(instances).insert(instanceCaptor.capture());
        assertEquals("A", instanceCaptor.getValue().getGradeCode());
        assertNull(instanceCaptor.getValue().getRouteSnapshot());
        assertTrue(nodeCaptor.getAllValues().stream().filter(node -> List.of("SECOND_LINE", "RND").contains(node.getNodeCode()))
                .allMatch(node -> "ROLE".equals(node.getApprovalModeCode()) && node.getCandidateRoleId() == 31L
                        && node.getCurrentApproverUserId() == null && node.getCandidateFactSnapshot() == null));
        assertTrue(instanceCaptor.getValue().getLeadTimeEnabled());
        var leadTime = CutoverApprovalStorage.leadTime(instanceCaptor.getValue());
        assertEquals("VERSION_UPGRADE", leadTime.cutoverType());
        assertEquals(2, leadTime.requiredDays());
        assertEquals(2, leadTime.actualNaturalDays());
        assertFalse(leadTime.lateSubmission());
        ArgumentCaptor<CutoverApprovalNotificationDO> notificationCaptor =
                ArgumentCaptor.forClass(CutoverApprovalNotificationDO.class);
        verify(notifications, times(4)).insert(notificationCaptor.capture());
        assertEquals(List.of("IN_PLATFORM", "SMS", "EMAIL", "DINGTALK"),
                notificationCaptor.getAllValues().stream().map(CutoverApprovalNotificationDO::getChannelCode).toList());
        assertTrue(notificationCaptor.getAllValues().stream()
                .allMatch(value -> "corr-1".equals(value.getCorrelationId())));
        assertTrue(notificationCaptor.getAllValues().stream().allMatch(value -> value.getNextRetryAt() == null));
        assertEquals(1, platform.facts.size());
        assertEquals(CutoverApprovalApplicationException.Code.IDEMPOTENCY_CONFLICT, conflict.code());
        if (replacesWithdrawn) {
            assertEquals(started.fact().approvalInstanceId(), previous.getReplacementApprovalInstanceId());
            assertEquals("WITHDRAWN", previous.getStatusCode());
            assertEquals("修订调研结果", previous.getWithdrawReason());
            verify(instances, times(1)).updateStateIfMatch(any());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void keepsGradeDLeadTimeDisabledAndHoldsAnUnresolvedServiceManager(boolean managerUnresolved) {
        CutoverApprovalSourceAssembler assembler = mock(CutoverApprovalSourceAssembler.class);
        CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        CutoverApprovalNotificationMapper notifications = mock(CutoverApprovalNotificationMapper.class);
        CutoverTaskDO task = new CutoverTaskDO(); task.setId(100L); task.setProjectId(10L);
        task.setCutoverType("VERSION_UPGRADE");
        task.setScheduledTime(LocalDateTime.of(2026, 9, 5, 8, 0));
        CutoverPlanRevisionDO plan = new CutoverPlanRevisionDO();
        plan.setSubmittedAt(LocalDateTime.of(2026, 9, 3, 18, 0));
        when(assembler.lockAndAssemble(any())).thenReturn(
                new CutoverApprovalSourceAssembler.LockedSource(task, null, null, plan, CutoverApprovalControlledPorts.approvalSource("D")));
        when(instances.insert(any(CutoverApprovalInstanceDO.class))).thenReturn(1);
        when(nodes.insert(any(CutoverApprovalNodeDO.class))).thenReturn(1);
        when(notifications.insert(any(CutoverApprovalNotificationDO.class))).thenReturn(1);
        var managers = CutoverApprovalControlledPorts.serviceManager(301L);
        if (managerUnresolved) {
            managers = mock(cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort.class);
            var missing = new cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort.ServiceManagerFact(
                    cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort.Outcome.NOT_UNIQUE,
                    1L, 10L, null, null, null, null, LocalDateTime.of(2026, 9, 1, 1, 0));
            when(managers.inspectCurrent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn(missing);
            when(managers.lockAndRevalidate(missing)).thenReturn(
                    new cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort.ServiceManagerRevalidation(
                            cn.iocoder.yudao.module.pms.cutover.service.approval.port.ProjectCutoverServiceManagerPort.Revalidation.STALE, missing));
        }
        CutoverApprovalApplicationService service = new CutoverApprovalApplicationService(assembler,
                instances,
                nodes,
                notifications,
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReassignmentMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper.class),
                managers,
                CutoverApprovalControlledPorts.roleCandidates(),
                CutoverApprovalControlledPorts.projectScope(202L),
                new DirectPlatform(),
                () -> 202L,
                Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverWorkflowCoordinator.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy.class));

        org.springframework.test.util.ReflectionTestUtils.setField(service, "storage", mock(CutoverApprovalStorage.class));
        service.start(new CutoverApprovalStartCommand(1L, 100L, 5L, 900L, 1,
                "D", 600L, 2L, null, null, 1, LocalDateTime.of(2026, 9, 3, 18, 0), null,
                "start-d", "corr-d"));

        ArgumentCaptor<CutoverApprovalInstanceDO> root = ArgumentCaptor.forClass(CutoverApprovalInstanceDO.class);
        verify(instances).insert(root.capture());
        assertFalse(root.getValue().getLeadTimeEnabled());
        assertNull(root.getValue().getLeadTimeSnapshot());
        assertEquals("PENDING", root.getValue().getStatusCode());
        assertEquals(managerUnresolved ? "ROUTE_CANDIDATE_NOT_UNIQUE" : null, root.getValue().getHoldReasonCode());
        if (managerUnresolved) {
            verify(notifications, never()).insert(any(CutoverApprovalNotificationDO.class));
            var capturedNodes = ArgumentCaptor.forClass(CutoverApprovalNodeDO.class);
            verify(nodes, times(2)).insert(capturedNodes.capture());
            var managerNode = capturedNodes.getAllValues().get(1);
            assertEquals("SERVICE_MANAGER", managerNode.getNodeCode());
            assertEquals("WAITING", managerNode.getStatusCode());
            assertNull(managerNode.getCurrentApproverUserId());
        }
    }

    @Test
    void pausesPendingApprovalWithoutWritingDecisionTime() {
        CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        CutoverApprovalInstanceDO pending = new CutoverApprovalInstanceDO();
        pending.setId(500L); pending.setTenantId(1L); pending.setTaskId(100L); pending.setPlanRevisionId(900L);
        pending.setPlanRevisionNo(1); pending.setStatusCode("PENDING"); pending.setSourceSnapshotVersion(1);
        pending.setVersion(3L);
        when(instances.selectByIdForUpdate(any())).thenReturn(pending);
        when(instances.updateStateIfMatch(any())).thenReturn(1);
        when(nodes.selectList(any())).thenReturn(List.of());
        CutoverApprovalApplicationService service = new CutoverApprovalApplicationService(mock(CutoverApprovalSourceAssembler.class),
                instances,
                nodes,
                mock(CutoverApprovalNotificationMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReviewItemMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalReassignmentMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskStageHistoryMapper.class),
                CutoverApprovalControlledPorts.serviceManager(301L),
                CutoverApprovalControlledPorts.roleCandidates(),
                CutoverApprovalControlledPorts.projectScope(202L),
                new DirectPlatform(),
                () -> 202L,
                Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverWorkflowCoordinator.class),
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.cutover.service.task.CutoverExecutionReadinessPolicy.class));

        CutoverApprovalCommandResult result = service.pause(new CutoverApprovalPauseCommand(
                1L, 500L, 3L, 900L, 1, "SOURCE_FACT_INVALIDATED", "pause-1", "corr-1"));

        assertEquals(ApprovalStatus.PAUSED_SOURCE_INVALIDATED, result.fact().status());
        assertNull(result.fact().decisionAt());
    }

    private static final class DirectPlatform implements PlatformCommandExecutionApi {
        private final Map<String, Cached> cache = new HashMap<>();
        private final List<SuccessFacts> facts = new ArrayList<>();
        @Override public <T> ExecutionResult<T> execute(IdempotencyScope scope, String digest, Class<T> type,
                Supplier<T> operation, Function<T, SuccessFacts> factory) {
            String key = scope.scopeCode() + ':' + scope.key(); Cached cached = cache.get(key);
            if (cached != null) return cached.digest.equals(digest)
                    ? new ExecutionResult<>(Decision.REPLAY_COMPLETED, type.cast(cached.response))
                    : new ExecutionResult<>(Decision.CONFLICT, null);
            T response = operation.get(); facts.add(factory.apply(response)); cache.put(key, new Cached(digest, response));
            return new ExecutionResult<>(Decision.NEW, response);
        }
        private record Cached(String digest, Object response) { }
    }
}
