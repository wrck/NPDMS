package cn.iocoder.yudao.module.pms.engineering.domain.briefing;

import java.util.Objects;

/**
 * 交底生成结果的不可变值。只接收服务端适配器返回的冻结来源和实际文件元数据。
 * inputVersion 是当前命令的并发水位，不是文件或不可变文档修订号。
 * 格式检查不等于文件验证；生成、审核通过及发布前还必须由适配器核验实际文件。
 */
public record BriefingDocumentArtifact(
        BriefingAggregate.Identity identity, int inputVersion,
        Long templateId, String templateSnapshot, String sourceSnapshot, String content,
        String fileUrl, String fileName, Long fileSize, String fileChecksum) {
    public BriefingDocumentArtifact {
        if (identity == null || identity.id() == null || inputVersion < 0
                || templateId == null || templateId <= 0
                || blank(templateSnapshot) || blank(sourceSnapshot) || blank(content)
                || blank(fileUrl) || fileUrl.length() > 512
                || blank(fileName) || fileName.length() > 200
                || fileSize == null || fileSize <= 0
                || fileChecksum == null || !fileChecksum.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("BRIEFING_DOCUMENT_ARTIFACT_INVALID");
    }

    public void requireTarget(BriefingAggregate.Identity expected, int expectedVersion) {
        if (!Objects.equals(identity, expected) || inputVersion != expectedVersion)
            throw new IllegalArgumentException("BRIEFING_DOCUMENT_TARGET_MISMATCH");
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
}
