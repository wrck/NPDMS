package cn.iocoder.yudao.module.pms.customer.service.contact;

public record ContactMasterWrite(Long customerId, Long contactId, Integer expectedVersion,
                                 ContactValues values, boolean primary, int status,
                                 boolean confirmNoPrimary) {
}
