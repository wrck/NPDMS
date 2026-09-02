package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import java.time.LocalDateTime;

public record CutoverSupportContactUpdate(Long tenantId, Long arrangementId, Long planRevisionId,
                                           Integer expectedVersion, String personName, String phone,
                                           LocalDateTime arrivalTime, String updater, LocalDateTime updateTime) {
}
