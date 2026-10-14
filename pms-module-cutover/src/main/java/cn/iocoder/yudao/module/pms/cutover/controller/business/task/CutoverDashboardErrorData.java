package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

public record CutoverDashboardErrorData(String category, String reasonCode,
                                        String recoveryAction, String ownerContext) {
}
