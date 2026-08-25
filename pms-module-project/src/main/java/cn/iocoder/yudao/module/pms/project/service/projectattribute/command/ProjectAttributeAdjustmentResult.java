package cn.iocoder.yudao.module.pms.project.service.projectattribute.command;

/** 属性调整后的版本与只读模板影响结论。 */
public record ProjectAttributeAdjustmentResult(
        Long projectId,
        Integer version,
        String matchResult,
        String impactResult,
        String operationId) {
}
