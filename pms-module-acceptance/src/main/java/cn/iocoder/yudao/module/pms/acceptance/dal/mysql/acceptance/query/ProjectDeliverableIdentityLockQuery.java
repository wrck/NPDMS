package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query;

public record ProjectDeliverableIdentityLockQuery(
        Long tenantId,
        Long projectId,
        String deliverableCode) {
}
