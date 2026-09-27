package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

import java.time.LocalDateTime;

public record CutoverApprovedContactVersionUpdate(Long tenantId, Long planRevisionId, Long expectedVersion,
                                                   Long newVersion, String updater, LocalDateTime updateTime) {
}
