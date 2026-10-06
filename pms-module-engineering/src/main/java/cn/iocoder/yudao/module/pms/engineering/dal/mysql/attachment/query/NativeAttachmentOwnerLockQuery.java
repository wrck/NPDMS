package cn.iocoder.yudao.module.pms.engineering.dal.mysql.attachment.query;

/** Lock the actual native attachment Owner in the authenticated tenant. */
public record NativeAttachmentOwnerLockQuery(Long tenantId, Long id) { }
