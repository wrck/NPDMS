package cn.iocoder.yudao.module.pms.cutover.controller.admin.dashboard;

import java.time.LocalDateTime;

public record CutoverDashboardKpiData(long todoCount, long archivedCount, long approvingCount,
                                      long rejectedPendingModificationCount, LocalDateTime generatedAt) {
}
