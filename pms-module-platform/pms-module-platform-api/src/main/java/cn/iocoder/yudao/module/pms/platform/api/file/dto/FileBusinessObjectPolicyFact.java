package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.util.Set;

public record FileBusinessObjectPolicyFact(
        boolean allowed,
        Long scopeVersion,
        String referenceMutability,
        String cardinality,
        Set<String> allowedCategoryCodes,
        Set<String> allowedMediaTypes,
        Long maxSizeBytes,
        String sensitivityCode) {

    public FileBusinessObjectPolicyFact {
        allowedCategoryCodes = allowedCategoryCodes == null ? Set.of() : Set.copyOf(allowedCategoryCodes);
        allowedMediaTypes = allowedMediaTypes == null ? Set.of() : Set.copyOf(allowedMediaTypes);
    }
}
