package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query;
import java.util.Set;
public record DeviceParsedLogVisibleHistoryQuery(Long tenantId, Long deviceId, Set<Long> visibleProjectIds) {}
