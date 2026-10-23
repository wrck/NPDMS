package cn.iocoder.yudao.module.pms.cutover.service.checklist.port;

import lombok.Builder;

public interface CutoverChecklistFilePort {

    FileFact lockAndRevalidate(CutoverChecklistFilePort.FileQuery query);

    @Builder
    record FileQuery(Long tenantId, Long actorId, Long projectId, Long checklistItemId,
                     long expectedScopeVersion, FileHandle handle) { }

    @Builder
    record FileHandle(Long artifactId, Integer versionNo, String referenceKey,
                      FileFactVersion fileFactVersion, Long scopeVersion) {
    }

    @Builder
    record FileFact(Long artifactId, Integer versionNo, String referenceKey,
                    FileFactVersion fileFactVersion, Long scopeVersion, String sha256) {
    }

    record FileFactVersion(Integer artifactVersion, Integer referenceVersion,
                           Integer availabilityVersion) {
    }
}
