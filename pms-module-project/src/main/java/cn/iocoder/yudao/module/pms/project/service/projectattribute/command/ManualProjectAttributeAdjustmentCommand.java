package cn.iocoder.yudao.module.pms.project.service.projectattribute.command;

/** 业务用户受控调整PROJ Owner属性的命令。 */
public record ManualProjectAttributeAdjustmentCommand(
        Long projectId,
        Long expectedVersion,
        String signingMethod,
        String projectCategory,
        String implementationMode,
        String adjustmentReason,
        String idempotencyKey,
        String requestDigest) {
}
