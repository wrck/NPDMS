package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query;

import java.time.LocalDateTime;

public record CutoverEffectivePublishedConfigurationQuery(Long tenantId, String configurationCode,
                                                          LocalDateTime taskCreatedAt) {
}
