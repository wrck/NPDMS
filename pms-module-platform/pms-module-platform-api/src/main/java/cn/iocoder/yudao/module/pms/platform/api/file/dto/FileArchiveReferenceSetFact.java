package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.List;

public record FileArchiveReferenceSetFact(
        String archiveBatchId,
        FileReferenceSetKey archiveSetKey,
        List<FileArtifactVersionFact> archivedFacts) {

    public FileArchiveReferenceSetFact {
        archivedFacts = archivedFacts == null ? List.of() : List.copyOf(archivedFacts);
    }
}
