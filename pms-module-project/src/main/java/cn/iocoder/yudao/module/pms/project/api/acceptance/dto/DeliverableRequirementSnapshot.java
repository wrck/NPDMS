package cn.iocoder.yudao.module.pms.project.api.acceptance.dto;

/** ACC-04 创建时冻结的最小交付件要求。 */
public record DeliverableRequirementSnapshot(
        String requirementKey,
        Long deliverableTemplateId,
        String deliverableType,
        String applicableStageCode,
        boolean required) {
}
