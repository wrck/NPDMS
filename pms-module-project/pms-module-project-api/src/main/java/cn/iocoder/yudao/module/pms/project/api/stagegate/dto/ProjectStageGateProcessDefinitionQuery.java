package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** 精确 ID 非空时只验证该定义，不回退最新版本；仅按 key 查询供新配置选择使用。 */
public record ProjectStageGateProcessDefinitionQuery(Long tenantId, String processDefinitionKey, String processDefinitionId) {
}
