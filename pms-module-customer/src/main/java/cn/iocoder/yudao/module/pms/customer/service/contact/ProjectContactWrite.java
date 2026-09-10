package cn.iocoder.yudao.module.pms.customer.service.contact;

public record ProjectContactWrite(Long projectId, Long contactId, Long sourceContactId,
                                  Integer expectedProjectVersion, Integer expectedVersion,
                                  ContactValues values, boolean primary, int status,
                                  boolean confirmNoPrimary) {}
