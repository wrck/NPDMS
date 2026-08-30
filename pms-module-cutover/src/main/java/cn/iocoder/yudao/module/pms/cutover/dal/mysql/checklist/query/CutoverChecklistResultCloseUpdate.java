package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

import java.time.LocalDateTime;

public record CutoverChecklistResultCloseUpdate(Long tenantId, Long resultId, LocalDateTime endedAt) {
}
