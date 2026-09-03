package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

import java.time.LocalDateTime;

public record CutoverChecklistSubmitUpdate(Long tenantId, Long checklistId, Integer expectedVersion,
                                           Long submittedBy, LocalDateTime submittedAt) {
}
