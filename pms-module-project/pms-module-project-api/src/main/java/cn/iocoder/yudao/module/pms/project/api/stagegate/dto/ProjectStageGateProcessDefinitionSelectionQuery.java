package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** 项目Gate范围内查询同key可启动定义身份。 */
public record ProjectStageGateProcessDefinitionSelectionQuery(
        Long tenantId,
        Long projectId,
        Long gateReferenceId,
        String processDefinitionKey) {
}
