package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.time.LocalDateTime;

public record DeliveryScopeProjectVersionSeed(Long id, Long tenantId, Long projectId,
                                              String actor, LocalDateTime now) {
}
