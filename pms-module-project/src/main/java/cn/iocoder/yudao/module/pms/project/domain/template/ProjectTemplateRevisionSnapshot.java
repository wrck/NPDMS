package cn.iocoder.yudao.module.pms.project.domain.template;

import java.util.List;

/** 创建决策可见的只读模板摘要，不包含绑定配置正文。 */
public record ProjectTemplateRevisionSnapshot(
        long revisionId,
        long templateId,
        String templateCode,
        int revisionNo,
        String templateName,
        String workflowDefinitionKey,
        int workflowDefinitionVersion,
        List<StageSummary> stages,
        List<MilestoneSummary> milestones,
        List<DeliverableSummary> deliverables,
        List<GateSummary> gates) {

    public ProjectTemplateRevisionSnapshot {
        stages = List.copyOf(stages);
        milestones = List.copyOf(milestones);
        deliverables = List.copyOf(deliverables);
        gates = List.copyOf(gates);
    }

    public record StageSummary(String stageCode, String stageName, int sortOrder, List<TaskSummary> tasks) {
        public StageSummary { tasks = List.copyOf(tasks); }
    }
    public record TaskSummary(String taskDefinitionKey, String name, String parentTaskDefinitionKey,
                              int sortOrder, String workBindingTypeCode) {}
    public record MilestoneSummary(String milestoneKey, String milestoneName, String stageCode) {}
    public record DeliverableSummary(String requirementKey, String deliverableName,
                                     String stageCode, boolean required) {}
    public record GateSummary(String gateKey, String gateName, String stageCode) {}
}
