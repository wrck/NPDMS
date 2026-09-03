package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ExternalApprovalNotificationDeliveryUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverExternalApprovalNotificationServiceTest {

    private static final LocalDateTime DUE_AT = LocalDateTime.of(2026, 9, 2, 12, 0);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void deliversThreeControlledResultsInsideTheProxiedTransaction() {
        Fixture fixture = fixture();
        fixture.port.result("SMS", new ExternalApprovalNotificationResult.Accepted("sms-accepted", DUE_AT));
        fixture.port.result("EMAIL", new ExternalApprovalNotificationResult.DeliveryUnknown("email-unknown"));
        fixture.port.result("DINGTALK", new ExternalApprovalNotificationResult.ExplicitFailure("DING_BUSY"));
        when(fixture.notifications.selectExternalDueForUpdateSkipLocked(any()))
                .thenReturn(List.of(row(501L, "SMS"), row(502L, "EMAIL"), row(503L, "DINGTALK")));
        when(fixture.notifications.updateExternalDeliveryIfMatch(any())).thenReturn(1);
        TenantContextHolder.setTenantId(1L);

        try (var context = new AnnotationConfigApplicationContext(
                ControlledExternalNotificationTransactionConfiguration.class)) {
            assertThat(AopUtils.isAopProxy(context.getBean(
                    CutoverExternalApprovalNotificationTransactionExecutor.class))).isTrue();
            var result = context.getBean(CutoverExternalApprovalNotificationService.class)
                    .deliverDue(1L, DUE_AT, 50);

            assertThat(result).isEqualTo(new CutoverExternalApprovalNotificationService.DeliveryResult(1, 1, 1));
        }

        assertThat(fixture.port.requests()).hasSize(3).allSatisfy(request -> {
            assertThat(request.correlationId()).isEqualTo("corr-node-1");
            assertThat(request.variables()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                    "taskCode", "CUT-10", "taskName", "核心割接", "grade", "A", "nodeName", "发起人"));
            assertThat(request.taskLink()).isEqualTo("/pms/cutover/cutover-task?taskId=10");
        });
        verify(fixture.notifications).updateExternalDeliveryIfMatch(argThat(update ->
                matches(update, 501L, "ACCEPTED", "sms-accepted", 0, null)));
        verify(fixture.notifications).updateExternalDeliveryIfMatch(argThat(update ->
                matches(update, 502L, "DELIVERY_UNKNOWN", "email-unknown", 0, null)));
        verify(fixture.notifications).updateExternalDeliveryIfMatch(argThat(update ->
                matches(update, 503L, "PENDING_RETRY", null, 1, "DING_BUSY")
                        && DUE_AT.plusMinutes(1).equals(update.nextRetryAt())));
        verifyNoInteractionsWithApprovalWriters(fixture);
    }

    @Test
    void secondClaimWithoutDueRowsDoesNotSendAgain() {
        Fixture fixture = fixture();
        fixture.port.result("SMS", new ExternalApprovalNotificationResult.Accepted("sms-accepted", DUE_AT));
        when(fixture.notifications.selectExternalDueForUpdateSkipLocked(any()))
                .thenReturn(List.of(row(501L, "SMS")), List.of());
        when(fixture.notifications.updateExternalDeliveryIfMatch(any())).thenReturn(1);
        TenantContextHolder.setTenantId(1L);

        try (var context = new AnnotationConfigApplicationContext(
                ControlledExternalNotificationTransactionConfiguration.class)) {
            var service = context.getBean(CutoverExternalApprovalNotificationService.class);
            assertThat(service.deliverDue(1L, DUE_AT, 50).accepted()).isEqualTo(1);
            assertThat(service.deliverDue(1L, DUE_AT, 50).accepted()).isZero();
        }

        assertThat(fixture.port.requests()).hasSize(1);
        verify(fixture.notifications, times(1)).updateExternalDeliveryIfMatch(any());
    }

    private static boolean matches(ExternalApprovalNotificationDeliveryUpdate update, long id, String status,
                                   String reference, int retries, String errorCode) {
        return update.notificationId() == id && status.equals(update.newStatusCode())
                && java.util.Objects.equals(reference, update.providerReferenceId())
                && update.retryCount() == retries && java.util.Objects.equals(errorCode, update.lastErrorCode());
    }

    private static void verifyNoInteractionsWithApprovalWriters(Fixture fixture) {
        verify(fixture.instances, never()).updateById(any(CutoverApprovalInstanceDO.class));
        verify(fixture.nodes, never()).updateById(any(CutoverApprovalNodeDO.class));
        verify(fixture.tasks, never()).updateById(any(CutoverTaskDO.class));
    }

    private static Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.notifications = mock(CutoverApprovalNotificationMapper.class);
        fixture.instances = mock(CutoverApprovalInstanceMapper.class);
        fixture.nodes = mock(CutoverApprovalNodeMapper.class);
        fixture.tasks = mock(CutoverTaskMapper.class);
        fixture.port = new ControlledCutoverExternalApprovalNotificationPort();
        ControlledExternalNotificationTransactionConfiguration.notifications = fixture.notifications;
        ControlledExternalNotificationTransactionConfiguration.instances = fixture.instances;
        ControlledExternalNotificationTransactionConfiguration.nodes = fixture.nodes;
        ControlledExternalNotificationTransactionConfiguration.tasks = fixture.tasks;
        ControlledExternalNotificationTransactionConfiguration.port = fixture.port;
        when(fixture.instances.selectById(100L)).thenReturn(root());
        when(fixture.nodes.selectById(101L)).thenReturn(node());
        when(fixture.tasks.selectById(10L)).thenReturn(task());
        return fixture;
    }

    private static CutoverApprovalNotificationDO row(long id, String channel) {
        CutoverApprovalNotificationDO row = new CutoverApprovalNotificationDO();
        row.setId(id); row.setTenantId(1L); row.setApprovalInstanceId(100L); row.setApprovalNodeId(101L);
        row.setRecipientUserId(11L); row.setChannelCode(channel); row.setTemplateCode("CUT_APPROVAL_PENDING_V2");
        row.setDeliveryKey("CUT_APPROVAL_EXT:100:1:0:" + channel); row.setCorrelationId("corr-node-1");
        row.setStatusCode("PENDING"); row.setRetryCount(0); row.setVersion(0);
        return row;
    }

    private static CutoverApprovalInstanceDO root() {
        CutoverApprovalInstanceDO root = new CutoverApprovalInstanceDO();
        root.setId(100L); root.setTenantId(1L); root.setTaskId(10L); root.setGradeCode("A");
        return root;
    }

    private static CutoverApprovalNodeDO node() {
        CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
        node.setId(101L); node.setTenantId(1L); node.setApprovalInstanceId(100L);
        node.setNodeNo(1); node.setNodeCode("INITIATOR");
        return node;
    }

    private static CutoverTaskDO task() {
        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(10L); task.setTenantId(1L); task.setTaskNo("CUT-10"); task.setTaskName("核心割接");
        return task;
    }

    private static final class Fixture {
        private CutoverApprovalNotificationMapper notifications;
        private CutoverApprovalInstanceMapper instances;
        private CutoverApprovalNodeMapper nodes;
        private CutoverTaskMapper tasks;
        private ControlledCutoverExternalApprovalNotificationPort port;
    }
}
