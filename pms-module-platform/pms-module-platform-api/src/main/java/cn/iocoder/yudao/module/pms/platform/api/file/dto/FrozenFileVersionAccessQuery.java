package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import lombok.Builder;

import java.util.Objects;
import java.util.Set;

/** Content access to an exact immutable owner version; never authorizes business submission. */
@Builder
public record FrozenFileVersionAccessQuery(FileBusinessObjectPolicyQuery scope,
                                          Long artifactId, Integer versionNo,
                                          Long expectedScopeVersion) {
    public FrozenFileVersionAccessQuery {
        Objects.requireNonNull(scope, "scope");
        if (!Set.of(FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW).contains(scope.requiredAction())
                || artifactId == null || artifactId <= 0 || versionNo == null || versionNo <= 0
                || expectedScopeVersion == null || expectedScopeVersion < 0) {
            throw new IllegalArgumentException("invalid frozen file content access");
        }
    }
}
