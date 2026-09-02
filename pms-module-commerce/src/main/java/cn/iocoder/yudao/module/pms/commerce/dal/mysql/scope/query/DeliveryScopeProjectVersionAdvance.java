package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.time.LocalDateTime;

public record DeliveryScopeProjectVersionAdvance(Long tenantId, Long projectId,
                                                 Long expectedScopeVersion, Integer expectedRowVersion,
                                                 Long newScopeVersion, Integer newPayloadVersion,
                                                 String changeType, String actor, LocalDateTime now) {
}
