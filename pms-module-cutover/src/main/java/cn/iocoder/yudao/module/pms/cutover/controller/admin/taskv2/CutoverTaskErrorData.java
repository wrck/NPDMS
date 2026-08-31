package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

/** F-CUT-002局部错误合同。不可得的当前版本保持null。 */
public record CutoverTaskErrorData(String category, String reasonCode, String recoveryAction,
                                   String ownerContext, Long currentTaskVersion,
                                   Long currentAssessmentVersion, Long currentOwnerVersion) {
}
