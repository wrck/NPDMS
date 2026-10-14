package cn.iocoder.yudao.module.pms.cutover.controller.business.task.vo;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import java.time.LocalDateTime;

public record CutoverTaskStatisticsRespVO(long todoCount, long archivedCount, long approvingCount,
                                         long rejectedPendingModificationCount, LocalDateTime generatedAt) {
    public static CutoverTaskStatisticsRespVO from(CutoverDashboardKpiView value) {
        return new CutoverTaskStatisticsRespVO(value.todoCount(), value.archivedCount(), value.approvingCount(),
                value.rejectedPendingModificationCount(), value.generatedAt());
    }
}
