package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeSnapshotCodec;
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
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalStartServiceTest {

    @Test
    void startsGradeARouteWithRealInitiatorAndOnePendingNotificationThenReplays() {
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
                new CutoverApprovalSourceAssembler.LockedSource(task, null, null, plan, "{}"));
        when(instances.insert(any(CutoverApprovalInstanceDO.class))).thenReturn(1);
        when(nodes.insert(any(CutoverApprovalNodeDO.class))).thenReturn(1);
        when(notifications.insert(any(CutoverApprovalNotificationDO.class))).thenReturn(1);
        DirectPlatform platform = new DirectPlatform();
        CutoverApprovalApplicationService service = new CutoverApprovalApplicationService(assembler, instances,
                nodes, notifications, CutoverApprovalControlledPorts.serviceManager(301L),
                CutoverApprovalControlledPorts.roleCandidates(), CutoverApprovalControlledPorts.projectScope(202L),
                platform, () -> 202L, Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC));
        CutoverApprovalStartCommand command = new CutoverApprovalStartCommand(1L, 100L, 5, 900L, 1,
                "A", 600L, 2, 700L, 3, 1, planSubmittedAt, null, "start-1", "corr-1");

        CutoverApprovalStartResult started = service.start(command);
        CutoverApprovalStartCommand sameBusinessNewCorrelation = new CutoverApprovalStartCommand(1L, 100L, 5,
                900L, 1, "A", 600L, 2, 700L, 3, 1, planSubmittedAt, null, "start-1", "corr-2");
        CutoverApprovalStartResult replayed = service.start(sameBusinessNewCorrelation);
        CutoverApprovalStartCommand changed = new CutoverApprovalStartCommand(1L, 100L, 6, 900L, 1,
                "A", 600L, 2, 700L, 3, 1, planSubmittedAt, null, "start-1", "corr-1");
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
        var routeSnapshot = JsonUtils.parseTree(instanceCaptor.getValue().getRouteSnapshot());
        assertEquals("A", routeSnapshot.path("grade").asText());
        assertEquals(List.of("INITIATOR", "SERVICE_MANAGER", "SECOND_LINE", "RND"),
                StreamSupport.stream(routeSnapshot.path("nodes").spliterator(), false)
                        .map(node -> node.path("nodeCode").asText()).toList());
        assertTrue(instanceCaptor.getValue().getLeadTimeEnabled());
        var leadTime = new CutoverLeadTimeSnapshotCodec().decode(instanceCaptor.getValue().getLeadTimeSnapshot());
        assertEquals("VERSION_UPGRADE", leadTime.cutoverType());
        assertEquals(2, leadTime.requiredDays());
        assertEquals(2, leadTime.actualNaturalDays());
        assertFalse(leadTime.lateSubmission());
        ArgumentCaptor<CutoverApprovalNotificationDO> notificationCaptor =
                ArgumentCaptor.forClass(CutoverApprovalNotificationDO.class);
        verify(notifications, times(4)).insert(notificationCaptor.capture());
        assertEquals(List.of("IN_PLATFORM", "SMS", "EMAIL", "DINGTALK"),
                notificationCaptor.getAllValues().stream().map(CutoverApprovalNotificationDO::getChannelCode).toList());
        assertTrue(notificationCaptor.getAllValues().stream().allMatch(value -> value.getNextRetryAt() == null));
        assertEquals(1, platform.facts.size());
        assertEquals(CutoverApprovalApplicationException.Code.IDEMPOTENCY_CONFLICT, conflict.code());
    }

    @Test
    void keepsGradeDLeadTimeDisabledWithoutReadingUnavailableInputs() {
        CutoverApprovalSourceAssembler assembler = mock(CutoverApprovalSourceAssembler.class);
        CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        CutoverApprovalNotificationMapper notifications = mock(CutoverApprovalNotificationMapper.class);
        CutoverTaskDO task = new CutoverTaskDO(); task.setId(100L); task.setProjectId(10L);
        when(assembler.lockAndAssemble(any())).thenReturn(
                new CutoverApprovalSourceAssembler.LockedSource(task, null, null, null, "{}"));
        when(instances.insert(any(CutoverApprovalInstanceDO.class))).thenReturn(1);
        when(nodes.insert(any(CutoverApprovalNodeDO.class))).thenReturn(1);
        when(notifications.insert(any(CutoverApprovalNotificationDO.class))).thenReturn(1);
        CutoverApprovalApplicationService service = new CutoverApprovalApplicationService(assembler, instances,
                nodes, notifications, CutoverApprovalControlledPorts.serviceManager(301L),
                CutoverApprovalControlledPorts.roleCandidates(), CutoverApprovalControlledPorts.projectScope(202L),
                new DirectPlatform(), () -> 202L,
                Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC));

        service.start(new CutoverApprovalStartCommand(1L, 100L, 5, 900L, 1,
                "D", 600L, 2, null, null, 1, LocalDateTime.of(2026, 9, 3, 18, 0), null,
                "start-d", "corr-d"));

        ArgumentCaptor<CutoverApprovalInstanceDO> root = ArgumentCaptor.forClass(CutoverApprovalInstanceDO.class);
        verify(instances).insert(root.capture());
        assertFalse(root.getValue().getLeadTimeEnabled());
        assertNull(root.getValue().getLeadTimeSnapshot());
    }

    @Test
    void pausesPendingApprovalWithoutWritingDecisionTime() {
        CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        CutoverApprovalInstanceDO pending = new CutoverApprovalInstanceDO();
        pending.setId(500L); pending.setTenantId(1L); pending.setTaskId(100L); pending.setPlanRevisionId(900L);
        pending.setPlanRevisionNo(1); pending.setStatusCode("PENDING"); pending.setSourceSnapshotVersion(1);
        pending.setVersion(3);
        when(instances.selectByIdForUpdate(any())).thenReturn(pending);
        when(instances.updateStateIfMatch(any())).thenReturn(1);
        when(nodes.selectList(any())).thenReturn(List.of());
        CutoverApprovalApplicationService service = new CutoverApprovalApplicationService(
                mock(CutoverApprovalSourceAssembler.class), instances, nodes,
                mock(CutoverApprovalNotificationMapper.class), CutoverApprovalControlledPorts.serviceManager(301L),
                CutoverApprovalControlledPorts.roleCandidates(), CutoverApprovalControlledPorts.projectScope(202L),
                new DirectPlatform(), () -> 202L,
                Clock.fixed(Instant.parse("2026-09-01T01:00:00Z"), ZoneOffset.UTC));

        CutoverApprovalCommandResult result = service.pause(new CutoverApprovalPauseCommand(
                1L, 500L, 3, 900L, 1, "SOURCE_FACT_INVALIDATED", "pause-1", "corr-1"));

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
