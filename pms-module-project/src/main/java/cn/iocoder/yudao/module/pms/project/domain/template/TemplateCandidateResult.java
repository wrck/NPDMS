package cn.iocoder.yudao.module.pms.project.domain.template;

import java.util.List;

public record TemplateCandidateResult(List<TemplateCandidate> candidates, String candidateWatermark) {

    public TemplateCandidateResult {
        candidates = List.copyOf(candidates);
    }
}
