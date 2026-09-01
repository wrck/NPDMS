package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionFact;

public record ProjectStageGateProcessDefinitionRespVO(
        String processDefinitionId, String processDefinitionKey, String name, boolean selectable) {
    public static ProjectStageGateProcessDefinitionRespVO from(ProjectStageGateProcessDefinitionFact fact) {
        return new ProjectStageGateProcessDefinitionRespVO(fact.processDefinitionId(),
                fact.processDefinitionKey(), fact.name(), fact.selectable());
    }
}
