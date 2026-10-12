package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query;
/** Sequence spans every project history for this tenant/device; callers hold the device lock. */
public record DeviceLogVersionQuery(Long tenantId, Long deviceId) { }
