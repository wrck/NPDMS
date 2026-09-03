package cn.iocoder.yudao.module.pms.cutover.service.dashboard.view;

import java.time.LocalDateTime;

public record CutoverDashboardKpiView(long todoCount, long archivedCount, long approvingCount,
                                      long rejectedPendingModificationCount, LocalDateTime generatedAt) {
}
