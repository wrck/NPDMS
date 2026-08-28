package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

import java.util.HashSet;
import java.util.List;

public record FileReferenceSetCollectionQuery(List<FileReferenceSetKey> collectionKeys,
                                              String requiredAction) {
    public FileReferenceSetCollectionQuery {
        collectionKeys = collectionKeys == null ? List.of() : List.copyOf(collectionKeys);
        if (collectionKeys.isEmpty() || collectionKeys.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(collectionKeys).size() != collectionKeys.size()) {
            throw new IllegalArgumentException("invalid file reference set keys");
        }
        requiredAction = FileActionCodes.requireSupported(requiredAction);
        if (!FileActionCodes.READ.equals(requiredAction)) {
            throw new IllegalArgumentException("reference set inspection requires READ");
        }
    }
}
