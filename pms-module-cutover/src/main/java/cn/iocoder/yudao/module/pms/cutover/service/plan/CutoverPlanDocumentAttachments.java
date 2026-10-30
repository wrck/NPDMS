package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.Set;

/** Only authorized exact-version content enters a generated plan; storage locations never cross the API. */
@Component
@RequiredArgsConstructor
public class CutoverPlanDocumentAttachments {
    private static final long MAX_FILE_BYTES = 52_428_800L;
    private final FileArtifactApi artifacts;
    private final AuthenticatedFileContentApi content;

    public record Query(Long tenantId, Long taskId, JsonNode file) {}
    public record GeneratedQuery(Long taskId, cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort.FileFact file) {}

    public CutoverPlanDocumentRenderer.Attachment resolve(Query query) {
        var file = query.file();
        long artifactId = Long.parseLong(file.path("artifactId").asText());
        int version = file.path("versionNo").asInt();
        String key = file.path("referenceKey").asText();
        var read = new FileArtifactVersionQuery(artifactId, version, "CUT", "CUTOVER_PLAN", query.taskId().toString(),
                "FULL_PLAN", key, FileActionCodes.DOWNLOAD);
        var fact = artifacts.inspect(read);
        if (fact == null || !Objects.equals(artifactId, fact.artifactId()) || !Objects.equals(version, fact.versionNo())
                || !Objects.equals(key, fact.referenceKey()) || !Set.of("ACTIVE", "ARCHIVED").contains(fact.referenceStatus())
                || !"AVAILABLE".equals(fact.availabilityStatus()) || !Objects.equals(file.path("sha256").asText(), fact.sha256())) {
            throw unavailable("方案引用附件版本不可用或内容版本不一致");
        }
        byte[] bytes = Set.of("image/png", "image/jpeg").contains(fact.mediaType())
                ? content.read(new AuthenticatedFileContentApi.ReadCommand(read, MAX_FILE_BYTES)) : null;
        return new CutoverPlanDocumentRenderer.Attachment(fact.name(), fact.mediaType(), bytes);
    }

    public boolean usesCurrentRendering(GeneratedQuery query) {
        var file = query.file();
        byte[] bytes = content.read(new AuthenticatedFileContentApi.ReadCommand(new FileArtifactVersionQuery(
                file.artifactId(), file.versionNo(), "CUT", "CUTOVER_PLAN", query.taskId().toString(), "FULL_PLAN",
                file.referenceKey(), FileActionCodes.DOWNLOAD), MAX_FILE_BYTES));
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(new ByteArrayInputStream(bytes))) {
            var property = document.getProperties().getCustomProperties().getProperty(CutoverPlanDocumentRenderer.FORMAT_PROPERTY);
            return property != null && CutoverPlanDocumentRenderer.FORMAT_VERSION.equals(property.getLpwstr());
        } catch (java.io.IOException ex) { throw unavailable("已生成方案文件不可读取"); }
    }

    private static CutoverPlanApplicationException unavailable(String message) {
        return new CutoverPlanApplicationException(CutoverPlanApplicationException.Code.FILE_FACT_STALE, message);
    }
}
