package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;
public record ProjectContactRestoreCommand(Long tenantId, Long projectId, Long contactId,
                                           Integer expectedVersion, Long actorUserId, Integer status) {}
