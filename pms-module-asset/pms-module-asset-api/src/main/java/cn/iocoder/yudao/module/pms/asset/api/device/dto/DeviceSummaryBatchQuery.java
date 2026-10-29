package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import java.util.Set;

/** 当前登录主体的设备摘要预取；projectId只是经Owner重新校验的可选查询提示。 */
public record DeviceSummaryBatchQuery(Long tenantId, Long actorId, Set<Long> deviceIds, Long projectId) { }
