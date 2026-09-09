package cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query;

public record ProjectContactDeleteCommand(Long tenantId, Long projectId, Long contactId, Integer expectedVersion, Long actorUserId) {}
