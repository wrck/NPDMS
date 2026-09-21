package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.module.pms.platform.api.file.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.*;

@Component @RequiredArgsConstructor
public class ProjectDocumentSourceRegistry {
    private final ObjectProvider<FileDocumentSourceProvider> providers;
    public List<FileDocumentSourceProvider.Descriptor> descriptors() {
        return providers.orderedStream().flatMap(p -> p.descriptors().stream()).sorted(Comparator.comparing(FileDocumentSourceProvider.Descriptor::code)).toList();
    }
    public FileDocumentSourceProvider.Scope resolve(Long tenant, FileEvidenceApi.Document file) {
        var matches = providers.orderedStream().map(p -> p.resolve(tenant, file.ownerContext(), file.objectType(), file.objectId(), file.purposeCode()))
                .filter(Objects::nonNull).toList();
        if (matches.size() > 1) throw new IllegalStateException("DOCUMENT_OWNER_AMBIGUOUS");
        return matches.isEmpty() ? null : matches.getFirst();
    }
    static boolean matches(tools.jackson.databind.JsonNode configuration, String sourceCode) {
        for (var source : configuration.path("automaticSources")) if (sourceCode.equals(source.asText())) return true;
        return false;
    }
}
