package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ApprovalNotificationDeliveryUpdate(Long tenantId, Long notificationId,
                                                  Integer expectedVersion, String expectedStatusCode,
                                                  String newStatusCode, Long messageId,
                                                  Integer retryCount, LocalDateTime nextRetryAt,
                                                  String lastErrorCode, LocalDateTime sentAt,
                                                  String updater, LocalDateTime updateTime) {
}
