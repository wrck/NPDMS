package cn.iocoder.yudao.module.pms.project.domain.template;

public record TemplateCandidate(
        long revisionId,
        long templateId,
        String templateCode,
        int revisionNo,
        String templateName,
        int matchPriority,
        boolean defaultCandidate,
        String contentSha256) {
}
