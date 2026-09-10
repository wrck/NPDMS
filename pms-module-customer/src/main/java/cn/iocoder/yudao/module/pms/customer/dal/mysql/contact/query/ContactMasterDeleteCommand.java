package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;

public record ContactMasterDeleteCommand(Long tenantId, Long customerId, Long contactId,
                                         Integer expectedVersion, String updater) {
}
