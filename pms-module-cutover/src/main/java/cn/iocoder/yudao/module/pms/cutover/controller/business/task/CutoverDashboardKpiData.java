package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import java.time.LocalDateTime;

public record CutoverDashboardKpiData(long todoCount, long archivedCount, long approvingCount,
                                      long rejectedPendingModificationCount, LocalDateTime generatedAt) {
}
