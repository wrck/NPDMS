package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query;

import java.time.LocalDateTime;

public record CutoverClosureVersionUpdate(Long tenantId, Long closureId, Integer expectedVersion,
                                          String updater, LocalDateTime updateTime) {
}
