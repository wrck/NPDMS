package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** 模板发布时校验冻结流程定义key。 */
public record ProjectStageGateProcessDefinitionQuery(Long tenantId, String processDefinitionKey) {
}
