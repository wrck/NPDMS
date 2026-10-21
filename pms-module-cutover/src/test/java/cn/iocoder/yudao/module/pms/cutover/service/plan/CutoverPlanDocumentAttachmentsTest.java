package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CutoverPlanDocumentAttachmentsTest {
    private final FileArtifactApi artifacts = mock(FileArtifactApi.class);
    private final AuthenticatedFileContentApi content = mock(AuthenticatedFileContentApi.class);
    private final CutoverPlanDocumentAttachments resolver = new CutoverPlanDocumentAttachments(artifacts, content);
    private final tools.jackson.databind.JsonNode file = JsonUtils.parseTree("{\"artifactId\":\"901\",\"versionNo\":2,\"referenceKey\":\"topology\",\"sha256\":\"" + "a".repeat(64) + "\"}");

    @Test
    void readsOnlyAuthorizedExactImageVersionWithTheExistingPlatformBound() {
        when(artifacts.inspect(any())).thenReturn(fact("image/png", "a".repeat(64)));
        byte[] bytes = {1, 2, 3}; when(content.read(any())).thenReturn(bytes);
        var attachment = resolver.resolve(new CutoverPlanDocumentAttachments.Query(1L, 50L, file));
        assertThat(attachment.content()).isSameAs(bytes); assertThat(attachment.name()).isEqualTo("拓扑.png");
        verify(content).read(new AuthenticatedFileContentApi.ReadCommand(new FileArtifactVersionQuery(901L, 2,
                "CUT", "CUTOVER_PLAN", "50", "FULL_PLAN", "topology", FileActionCodes.DOWNLOAD), 52_428_800L));
    }

    @Test
    void refusesAReferenceOutsideTheExactTaskAndChecksHashBeforeReadingBytes() {
        var exact = new FileArtifactVersionQuery(901L, 2, "CUT", "CUTOVER_PLAN", "50", "FULL_PLAN", "topology", FileActionCodes.DOWNLOAD);
        when(artifacts.inspect(exact)).thenReturn(fact("image/png", "a".repeat(64)));
        assertThatThrownBy(() -> resolver.resolve(new CutoverPlanDocumentAttachments.Query(1L, 999L, file)))
                .isInstanceOf(CutoverPlanApplicationException.class);
        when(artifacts.inspect(exact)).thenReturn(fact("image/png", "b".repeat(64)));
        assertThatThrownBy(() -> resolver.resolve(new CutoverPlanDocumentAttachments.Query(1L, 50L, file)))
                .isInstanceOf(CutoverPlanApplicationException.class);
        verifyNoInteractions(content);
    }

    @Test void rejectsUnexpectedArtifactVersionReferenceAndUnavailableFactsEvenIfThePublicOwnerResponseIsCorrupt() {
        for (String change : java.util.List.of("artifact", "version", "reference", "inactive", "unavailable")) {
            reset(artifacts, content);
            when(artifacts.inspect(any())).thenReturn(new FileArtifactVersionFact(change.equals("artifact") ? 999L : 901L,
                    change.equals("version") ? 9 : 2, change.equals("reference") ? "other" : "topology", "CUTOVER_PLAN", "拓扑.png",
                    3L, "image/png", "a".repeat(64), change.equals("unavailable") ? "UNAVAILABLE" : "AVAILABLE",
                    change.equals("inactive") ? "DETACHED" : "ACTIVE", new FileFactVersion(1, 1, 1), 1L));
            assertThatThrownBy(() -> resolver.resolve(new CutoverPlanDocumentAttachments.Query(1L, 50L, file)))
                    .as(change).isInstanceOf(CutoverPlanApplicationException.class);
            verifyNoInteractions(content);
        }
    }

    @Test
    void keepsNonImageReferencesWithoutReadingTheirContentAndDoesNotHidePermissionFailures() {
        when(artifacts.inspect(any())).thenReturn(fact("application/pdf", "a".repeat(64)));
        assertThat(resolver.resolve(new CutoverPlanDocumentAttachments.Query(1L, 50L, file)).content()).isNull();
        verifyNoInteractions(content);
        when(artifacts.inspect(any())).thenReturn(fact("image/jpeg", "a".repeat(64)));
        when(content.read(any())).thenThrow(new SecurityException("denied"));
        assertThatThrownBy(() -> resolver.resolve(new CutoverPlanDocumentAttachments.Query(1L, 50L, file)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void oldGeneratedDocumentsAreNotMistakenForTheNewImageRenderingCache() throws Exception {
        var fact = cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort.FileFact.builder()
                .artifactId(901L).versionNo(2).referenceKey("topology").scopeVersion(1L).sha256("a".repeat(64))
                .fileFactVersion(new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort.FileFactVersion(1, 1, 1)).build();
        for (boolean current : new boolean[]{false, true}) {
            try (var doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(); var output = new java.io.ByteArrayOutputStream()) {
                if (current) doc.getProperties().getCustomProperties().addProperty(CutoverPlanDocumentRenderer.FORMAT_PROPERTY, CutoverPlanDocumentRenderer.FORMAT_VERSION);
                doc.write(output); when(content.read(any())).thenReturn(output.toByteArray());
                assertThat(resolver.usesCurrentRendering(new CutoverPlanDocumentAttachments.GeneratedQuery(50L, fact))).isEqualTo(current);
            }
        }
    }

    private static FileArtifactVersionFact fact(String media, String sha) {
        return new FileArtifactVersionFact(901L, 2, "topology", "CUTOVER_PLAN", "拓扑.png", 3L, media, sha,
                "AVAILABLE", "ACTIVE", new FileFactVersion(1, 1, 1), 1L);
    }
}
