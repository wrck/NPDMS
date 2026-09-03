package cn.iocoder.yudao.module.pms.cutover.service.approval.notification;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalNotificationTransactionBoundaryTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void providerRollbackDoesNotRollbackPendingRetry() throws Exception {
        TxConfig.dataSource = mock(DataSource.class);
        TxConfig.outerConnection = mock(Connection.class);
        TxConfig.providerConnection = mock(Connection.class);
        when(TxConfig.dataSource.getConnection()).thenReturn(TxConfig.outerConnection, TxConfig.providerConnection);
        when(TxConfig.outerConnection.getAutoCommit()).thenReturn(true);
        when(TxConfig.providerConnection.getAutoCommit()).thenReturn(true);
        TxConfig.notifications = mock(CutoverApprovalNotificationMapper.class);
        TxConfig.instances = mock(CutoverApprovalInstanceMapper.class);
        TxConfig.nodes = mock(CutoverApprovalNodeMapper.class);
        TxConfig.tasks = mock(CutoverTaskMapper.class);
        TxConfig.notify = mock(NotifyMessageSendApi.class);
        givenPendingFacts();
        when(TxConfig.notify.sendSingleMessageToAdmin(any())).thenThrow(new IllegalStateException("provider tx failed"));
        TenantContextHolder.setTenantId(1L);

        try (var context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            var result = context.getBean(CutoverApprovalNotificationService.class)
                    .deliverDue(1L, LocalDateTime.of(2026, 9, 2, 11, 0), 50);

            assertThat(result).isEqualTo(new CutoverApprovalNotificationService.DeliveryResult(0, 1));
        }
        verify(TxConfig.providerConnection).rollback();
        verify(TxConfig.outerConnection).commit();
        verify(TxConfig.outerConnection, never()).rollback();
        verify(TxConfig.notifications).updateDeliveryIfMatch(argThat(update ->
                "PENDING_RETRY".equals(update.newStatusCode()) && update.retryCount() == 1));
    }

    private static void givenPendingFacts() {
        CutoverApprovalNotificationDO notification = new CutoverApprovalNotificationDO();
        notification.setId(500L); notification.setTenantId(1L); notification.setApprovalInstanceId(100L);
        notification.setApprovalNodeId(101L); notification.setRecipientUserId(11L);
        notification.setDeliveryKey("CUT_APPROVAL:100:1:0"); notification.setTemplateCode("CUT_APPROVAL_PENDING");
        notification.setStatusCode("PENDING"); notification.setRetryCount(0); notification.setVersion(0);
        when(TxConfig.notifications.selectDueForUpdateSkipLocked(any())).thenReturn(List.of(notification));
        when(TxConfig.notifications.updateDeliveryIfMatch(any())).thenReturn(1);
        CutoverApprovalInstanceDO root = new CutoverApprovalInstanceDO();
        root.setId(100L); root.setTenantId(1L); root.setTaskId(10L);
        CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
        node.setId(101L); node.setTenantId(1L); node.setApprovalInstanceId(100L); node.setNodeNo(1);
        node.setNodeCode("INITIATOR");
        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(10L); task.setTenantId(1L); task.setTaskNo("CUT-10"); task.setTaskName("割接任务");
        when(TxConfig.instances.selectById(100L)).thenReturn(root);
        when(TxConfig.nodes.selectById(101L)).thenReturn(node);
        when(TxConfig.tasks.selectById(10L)).thenReturn(task);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TxConfig {
        static DataSource dataSource;
        static Connection outerConnection;
        static Connection providerConnection;
        static CutoverApprovalNotificationMapper notifications;
        static CutoverApprovalInstanceMapper instances;
        static CutoverApprovalNodeMapper nodes;
        static CutoverTaskMapper tasks;
        static NotifyMessageSendApi notify;

        @Bean DataSource dataSource() { return dataSource; }
        @Bean PlatformTransactionManager transactionManager() { return new DataSourceTransactionManager(dataSource); }
        @Bean CutoverApprovalNotificationProviderExecutor providerExecutor() {
            return new CutoverApprovalNotificationProviderExecutor(notify);
        }
        @Bean CutoverApprovalNotificationService notificationService(
                CutoverApprovalNotificationProviderExecutor executor) {
            return new CutoverApprovalNotificationService(notifications, instances, nodes, tasks, executor);
        }
    }
}
