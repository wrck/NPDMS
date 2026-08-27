package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

import java.util.HashSet;
import java.util.List;

public record FileReferenceSetCollectionRevalidationQuery(List<FileReferenceSetExpectation> collections,
                                                          String requiredAction) {
    public FileReferenceSetCollectionRevalidationQuery {
        collections = collections == null ? List.of() : List.copyOf(collections);
        if (collections.isEmpty() || collections.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(collections.stream().map(FileReferenceSetExpectation::key).toList()).size()
                != collections.size()) {
            throw new IllegalArgumentException("invalid file reference set expectations");
        }
        requiredAction = FileActionCodes.requireSupported(requiredAction);
        if (!FileActionCodes.READ.equals(requiredAction)) {
            throw new IllegalArgumentException("reference set revalidation requires READ");
        }
    }
}
