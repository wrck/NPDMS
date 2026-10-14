package cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover.query;

/** 割接成功事实消费时，锁定当前设备的直接归属范围。 */
public record DeviceCutoverScopeQuery(Long tenantId, Long deviceId) { }
