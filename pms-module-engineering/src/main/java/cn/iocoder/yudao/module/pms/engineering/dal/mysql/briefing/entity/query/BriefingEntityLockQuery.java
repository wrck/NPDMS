package cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.entity.query;

/** 单一租户内锁定交底对象；只允许在业务事务中使用。 */
public record BriefingEntityLockQuery(Long tenantId, Long id) {}
