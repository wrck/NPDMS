package cn.iocoder.yudao.module.pms.platform.dal.mysql.command.query;

/** 平台幂等记录稳定复合业务键查询。 */
public record IdempotencyScopeQuery(Long tenantId, String scopeCode, Long actorId,
                                    String idempotencyKey) {
}
