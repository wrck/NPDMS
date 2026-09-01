package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** Gate流程启动或幂等重放后的实际实例身份。 */
public record ProjectStageGateProcessStartFact(
        String processInstanceId,
        String processDefinitionId,
        String processDefinitionKey,
        String businessKey,
        String outcome) {
}
