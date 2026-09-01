package cn.iocoder.yudao.module.pms.cutover.service.approval.notification;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNotificationDeliveryUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalNotificationServiceTest {

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void sendsPendingNotificationAndPersistsSentResult() {
        Fixture f = new Fixture();
        f.givenPending();
        when(f.notify.sendSingleMessageToAdmin(any())).thenReturn(900L);

        var result = f.service.deliverDue(1L, f.now, 50);

        assertThat(result).isEqualTo(new CutoverApprovalNotificationService.DeliveryResult(1, 0));
        ArgumentCaptor<NotifySendSingleToUserReqDTO> request = ArgumentCaptor.forClass(NotifySendSingleToUserReqDTO.class);
        verify(f.notify).sendSingleMessageToAdmin(request.capture());
        assertThat(request.getValue().getDeliveryKey()).isEqualTo("CUT_APPROVAL:100:1:0");
        assertThat(request.getValue().getTemplateParams()).containsEntry("taskCode", "CUT-10")
                .containsEntry("nodeCode", "INITIATOR")
                .containsEntry("link", "/pms/cutover/cutover-task?taskId=10");
        verify(f.notifications).updateDeliveryIfMatch(argThat(update -> sent(update, f.now)));
    }

    @Test
    void schedulesFirstRetryWithoutChangingApprovalFacts() {
        Fixture f = new Fixture();
        f.givenPending();
        when(f.notify.sendSingleMessageToAdmin(any())).thenThrow(new IllegalStateException("system unavailable"));

        var result = f.service.deliverDue(1L, f.now, 50);

        assertThat(result).isEqualTo(new CutoverApprovalNotificationService.DeliveryResult(0, 1));
        verify(f.notifications).updateDeliveryIfMatch(argThat(update ->
                "PENDING_RETRY".equals(update.newStatusCode()) && update.retryCount() == 1
                        && f.now.plusMinutes(1).equals(update.nextRetryAt())
                        && "NOTIFY_PROVIDER_FAILURE".equals(update.lastErrorCode())));
        verify(f.instances, never()).updateAfterReassignmentIfMatch(any());
        verify(f.nodes, never()).updateStatusIfMatch(any());
        verify(f.tasks, never()).transitionFromApprovalIfMatch(any());
    }

    private static boolean sent(ApprovalNotificationDeliveryUpdate update, LocalDateTime now) {
        return "SENT".equals(update.newStatusCode()) && Long.valueOf(900L).equals(update.messageId())
                && update.retryCount() == 0 && update.nextRetryAt() == null && update.lastErrorCode() == null
                && now.equals(update.sentAt());
    }

    private static final class Fixture {
        final CutoverApprovalNotificationMapper notifications = mock(CutoverApprovalNotificationMapper.class);
        final CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        final CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final NotifyMessageSendApi notify = mock(NotifyMessageSendApi.class);
        final CutoverApprovalNotificationService service = new CutoverApprovalNotificationService(
                notifications, instances, nodes, tasks, notify);
        final LocalDateTime now = LocalDateTime.of(2026, 9, 2, 10, 0);

        void givenPending() {
            CutoverApprovalNotificationDO notification = new CutoverApprovalNotificationDO();
            notification.setId(500L); notification.setTenantId(1L); notification.setApprovalInstanceId(100L);
            notification.setApprovalNodeId(101L); notification.setRecipientUserId(11L);
            notification.setDeliveryKey("CUT_APPROVAL:100:1:0"); notification.setTemplateCode("CUT_APPROVAL_PENDING");
            notification.setStatusCode("PENDING"); notification.setRetryCount(0); notification.setVersion(0);
            when(notifications.selectDueForUpdateSkipLocked(any())).thenReturn(List.of(notification));
            when(notifications.updateDeliveryIfMatch(any())).thenReturn(1);
            CutoverApprovalInstanceDO root = new CutoverApprovalInstanceDO();
            root.setId(100L); root.setTenantId(1L); root.setTaskId(10L);
            CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
            node.setId(101L); node.setTenantId(1L); node.setApprovalInstanceId(100L); node.setNodeNo(1);
            node.setNodeCode("INITIATOR"); node.setCurrentApproverUserId(44L);
            CutoverTaskDO task = new CutoverTaskDO();
            task.setId(10L); task.setTenantId(1L); task.setTaskNo("CUT-10"); task.setTaskName("割接任务");
            when(instances.selectById(100L)).thenReturn(root);
            when(nodes.selectById(101L)).thenReturn(node);
            when(tasks.selectById(10L)).thenReturn(task);
        }
    }
}
