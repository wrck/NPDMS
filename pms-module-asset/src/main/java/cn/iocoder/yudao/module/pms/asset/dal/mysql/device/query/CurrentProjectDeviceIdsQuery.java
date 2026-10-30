package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

import java.util.Set;

/** 已由服务端确认FULL项目范围后，读取本次设备ID在该项目内的当前记录。 */
public record CurrentProjectDeviceIdsQuery(Long tenantId, Long projectId, Set<Long> deviceIds) { }
