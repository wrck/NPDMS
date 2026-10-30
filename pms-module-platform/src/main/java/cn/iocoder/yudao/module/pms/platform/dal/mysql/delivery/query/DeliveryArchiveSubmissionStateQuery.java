package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

public record DeliveryArchiveSubmissionStateQuery(Long tenantId, Long submissionId, String archiveStatus, String failureCode) {}
