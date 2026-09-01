package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import java.time.LocalDateTime;

public record CutoverApprovedContactVersionUpdate(Long tenantId, Long planRevisionId, Integer expectedVersion,
                                                   Integer newVersion, String updater, LocalDateTime updateTime) {
}
