package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record FileReferenceSetFact(FileReferenceSetKey key, Long scopeVersion,
                                   List<FileArtifactVersionFact> activeFacts) {
    public FileReferenceSetFact {
        if (key == null || scopeVersion == null || scopeVersion < 0) {
            throw new IllegalArgumentException("invalid file reference set fact");
        }
        activeFacts = activeFacts == null ? List.of() : List.copyOf(activeFacts);
        String previous = null;
        for (FileArtifactVersionFact fact : activeFacts) {
            if (fact == null || !"ACTIVE".equals(fact.referenceStatus())
                    || !scopeVersion.equals(fact.scopeVersion()) || fact.referenceKey() == null
                    || (previous != null && previous.compareTo(fact.referenceKey()) >= 0)) {
                throw new IllegalArgumentException("invalid ordered active file reference facts");
            }
            previous = fact.referenceKey();
        }
    }
}
