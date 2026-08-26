package cn.iocoder.yudao.module.pms.engineering.api.preparation.dto;

/** PRE-02初始化的稳定业务结果。 */
public record PreparationInitializationResult(
        Long preparationId,
        Long projectId,
        Integer businessVersion,
        Integer preparationVersion) {
}
