package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query;

import java.time.LocalDateTime;

/** F-CUT-002 创建上下文读取当前适用的发布配置。 */
public record CutoverEffectiveConfigurationListQuery(Long tenantId, LocalDateTime effectiveAt) {
}
