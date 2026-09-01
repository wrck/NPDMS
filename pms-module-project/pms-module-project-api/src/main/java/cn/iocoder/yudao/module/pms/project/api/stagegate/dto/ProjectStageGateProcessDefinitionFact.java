package cn.iocoder.yudao.module.pms.project.api.stagegate.dto;

/** Flowable原生流程定义身份；不增加PMS版本字段。 */
public record ProjectStageGateProcessDefinitionFact(
        String processDefinitionId,
        String processDefinitionKey,
        String name,
        boolean selectable) {
}
