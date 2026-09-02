package cn.iocoder.yudao.module.pms.cutover.controller.admin.dashboard;

public record CutoverDashboardErrorData(String category, String reasonCode,
                                        String recoveryAction, String ownerContext) {
}
