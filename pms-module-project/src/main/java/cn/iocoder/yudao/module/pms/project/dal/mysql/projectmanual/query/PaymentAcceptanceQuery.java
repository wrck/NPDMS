package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** Exact contract and explicitly configured node code, always tenant scoped. */
public record PaymentAcceptanceQuery(Long tenantId, String contractNo, String nodeType, String nodeCode, boolean lock) {}
