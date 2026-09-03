package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true)
class ControlledExternalNotificationTransactionConfiguration {

    static CutoverApprovalNotificationMapper notifications;
    static CutoverApprovalInstanceMapper instances;
    static CutoverApprovalNodeMapper nodes;
    static CutoverTaskMapper tasks;
    static ControlledCutoverExternalApprovalNotificationPort port;

    @Bean
    PlatformTransactionManager transactionManager() {
        return new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
    }

    @Bean
    CutoverExternalApprovalNotificationTransactionExecutor externalNotificationExecutor() {
        return new CutoverExternalApprovalNotificationTransactionExecutor(
                notifications, instances, nodes, tasks, port);
    }

    @Bean
    CutoverExternalApprovalNotificationService externalNotificationService(
            CutoverExternalApprovalNotificationTransactionExecutor executor) {
        return new CutoverExternalApprovalNotificationService(executor);
    }
}
