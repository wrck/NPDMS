package cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo;

import cn.iocoder.yudao.module.pms.project.domain.template.ProjectTemplateRevisionSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "管理后台 - PM-03 项目模板受控预览")
public record ProjectTemplatePreviewRespVO(
        long revisionId, long templateId, String templateCode, int revisionNo, String templateName,
        String workflowDefinitionKey, int workflowDefinitionVersion,
        List<ProjectTemplateRevisionSnapshot.StageSummary> stages,
        List<ProjectTemplateRevisionSnapshot.MilestoneSummary> milestones,
        List<ProjectTemplateRevisionSnapshot.DeliverableSummary> deliverables,
        List<ProjectTemplateRevisionSnapshot.GateSummary> gates) {

    public static ProjectTemplatePreviewRespVO from(ProjectTemplateRevisionSnapshot snapshot) {
        return new ProjectTemplatePreviewRespVO(snapshot.revisionId(), snapshot.templateId(), snapshot.templateCode(),
                snapshot.revisionNo(), snapshot.templateName(), snapshot.workflowDefinitionKey(),
                snapshot.workflowDefinitionVersion(), snapshot.stages(), snapshot.milestones(),
                snapshot.deliverables(), snapshot.gates());
    }
}
