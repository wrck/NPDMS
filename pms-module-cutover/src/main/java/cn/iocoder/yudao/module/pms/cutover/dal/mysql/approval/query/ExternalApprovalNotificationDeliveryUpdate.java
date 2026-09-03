package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query;

import java.time.LocalDateTime;

public record ExternalApprovalNotificationDeliveryUpdate(Long tenantId, Long notificationId,
                                                          String channelCode, Integer expectedVersion,
                                                          String expectedStatusCode, String newStatusCode,
                                                          String providerReferenceId, Integer retryCount,
                                                          LocalDateTime nextRetryAt, String lastErrorCode,
                                                          LocalDateTime lastAttemptAt, String updater,
                                                          LocalDateTime updateTime) {
}
