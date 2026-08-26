package cn.iocoder.yudao.module.pms.engineering.api.preparation.dto;

/** PRE-02初始化只接受已冻结标识与受信操作者，不接受tenant、模板或Schema自报。 */
public record PreparationInitializationCommand(
        Long projectId,
        Long projectTaskId,
        Long executionContractId,
        Integer expectedProjectVersion,
        Integer expectedProjectTaskVersion,
        Integer expectedContractVersion,
        String triggerType,
        String idempotencyKey,
        String operationId,
        Long actorUserId) {
}
