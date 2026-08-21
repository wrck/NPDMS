package cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateCandidateResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "管理后台 - PM-03 项目模板候选响应")
public record ProjectTemplateCandidateRespVO(List<Candidate> candidates, String candidateWatermark) {

    public static ProjectTemplateCandidateRespVO from(TemplateCandidateResult result) {
        return new ProjectTemplateCandidateRespVO(result.candidates().stream()
                .map(item -> new Candidate(item.revisionId(), item.templateId(), item.templateCode(), item.revisionNo(),
                        item.templateName(), item.matchPriority(), item.defaultCandidate())).toList(),
                result.candidateWatermark());
    }

    public record Candidate(long revisionId, long templateId, String templateCode, int revisionNo,
                            String templateName, int matchPriority, boolean defaultCandidate) {}
}
