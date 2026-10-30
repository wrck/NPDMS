package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

public record ProjectContactCustomerUpdate(Long tenantId, Long projectId, Long expectedVersion,
                                           Long customerId, String customerCode, String customerName, String updater) {}
