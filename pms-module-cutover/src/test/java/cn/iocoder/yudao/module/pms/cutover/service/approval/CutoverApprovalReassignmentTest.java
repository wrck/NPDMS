package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.ReassignCutoverApprovalCommand;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalReassignmentTest {

    @Test
    void reassignsPendingInitiatorAndAppendsHistoryAndNotification() {
        CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        CutoverApprovalNotificationMapper notifications = mock(CutoverApprovalNotificationMapper.class);
        CutoverApprovalReviewItemMapper reviews = mock(CutoverApprovalReviewItemMapper.class);
        CutoverApprovalReassignmentMapper reassignments = mock(CutoverApprovalReassignmentMapper.class);
        CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        CutoverTaskStageHistoryMapper histories = mock(CutoverTaskStageHistoryMapper.class);
        ProjectCutoverServiceManagerPort managers = mock(ProjectCutoverServiceManagerPort.class);
        CutoverApprovalRoleCandidatePort candidates = mock(CutoverApprovalRoleCandidatePort.class);
        CutoverApprovalProjectScopePort scopes = mock(CutoverApprovalProjectScopePort.class);
        PlatformCommandExecutionApi platform = mock(PlatformCommandExecutionApi.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T01:00:00Z"), ZoneOffset.UTC);
        CutoverTaskDO task = task(); CutoverApprovalInstanceDO root = root(); CutoverApprovalNodeDO node = node();
        when(tasks.selectForUpdate(any())).thenReturn(task);
        when(instances.selectByIdForUpdate(any())).thenReturn(root);
        when(nodes.selectByInstanceAndNodeForUpdate(any())).thenReturn(node);
        when(nodes.updateStatusIfMatch(any())).thenReturn(1);
        when(reassignments.selectMaxReassignmentNo(any())).thenReturn(0);
        when(reassignments.insert(any(CutoverApprovalReassignmentDO.class))).thenReturn(1);
        when(instances.updateAfterReassignmentIfMatch(any())).thenReturn(1);
        when(notifications.insert(any(CutoverApprovalNotificationDO.class))).thenReturn(1);
        when(nodes.selectList(any())).thenAnswer(ignored -> List.of(node));
        var scope = new CutoverApprovalProjectScopePort.ProjectScopeFact(1L, 20L, 44L, "ACTION_EDIT", true, 8L);
        when(scopes.inspect(1L, 20L, 44L, "ACTION_EDIT")).thenReturn(scope);
        when(scopes.lockAndRevalidate(scope)).thenReturn(new CutoverApprovalProjectScopePort.ProjectScopeRevalidation(
                CutoverApprovalProjectScopePort.Revalidation.VALID, scope));
        when(platform.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") java.util.function.Supplier<Object> operation = invocation.getArgument(3);
            Object response = operation.get();
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, response);
        });
        CutoverApprovalApplicationService service = new CutoverApprovalApplicationService(null, instances, nodes,
                notifications, reviews, reassignments, tasks, histories, managers, candidates, scopes, platform,
                () -> 99L, clock);

        var result = service.reassign(new ReassignCutoverApprovalCommand(1L, 10L, 3, 100L, 0,
                1, 44L, "转交当前项目负责人", "key-r1", "corr-r1"));

        assertThat(result.approvalVersion()).isEqualTo(1);
        assertThat(result.nodes()).singleElement().satisfies(value -> {
            assertThat(value.currentApproverUserId()).isEqualTo(44L);
            assertThat(value.nodeVersion()).isEqualTo(1);
        });
        assertThat(node.getOriginalApproverUserId()).isEqualTo(11L);
        verify(reassignments).insert(argThat((CutoverApprovalReassignmentDO row) ->
                row.getFromApproverUserId().equals(11L) && row.getToApproverUserId().equals(44L)
                        && row.getReassignmentNo() == 1 && row.getOperatedBy().equals(99L)));
        verify(notifications).insert(argThat((CutoverApprovalNotificationDO row) ->
                row.getRecipientUserId().equals(44L) && "PENDING".equals(row.getStatusCode())));
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO(); row.setId(10L); row.setTenantId(1L); row.setVersion(3);
        row.setTaskNo("CUT-10"); row.setTaskName("割接任务"); row.setCurrentStage("P5"); row.setTaskStatus("APPROVING");
        return row;
    }
    private static CutoverApprovalInstanceDO root() {
        CutoverApprovalInstanceDO row = new CutoverApprovalInstanceDO(); row.setId(100L); row.setTenantId(1L);
        row.setTaskId(10L); row.setProjectId(20L); row.setGradeCode("A"); row.setStatusCode("PENDING");
        row.setHoldReasonCode("APPROVER_UNAVAILABLE"); row.setCurrentNodeNo(1); row.setVersion(0); return row;
    }
    private static CutoverApprovalNodeDO node() {
        CutoverApprovalNodeDO row = new CutoverApprovalNodeDO(); row.setId(101L); row.setTenantId(1L);
        row.setApprovalInstanceId(100L); row.setNodeNo(1); row.setNodeCode("INITIATOR"); row.setStatusCode("PENDING");
        row.setOriginalApproverUserId(11L); row.setCurrentApproverUserId(11L); row.setProjectScopeVersion(7L);
        row.setCandidateFactSnapshot("{}"); row.setVersion(0); return row;
    }
}
