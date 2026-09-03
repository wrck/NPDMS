package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

public interface CutoverExternalApprovalNotificationPort {

    ExternalApprovalNotificationResult send(ExternalApprovalNotificationRequest request);
}
