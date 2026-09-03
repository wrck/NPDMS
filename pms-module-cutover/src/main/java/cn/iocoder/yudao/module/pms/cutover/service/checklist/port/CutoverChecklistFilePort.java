package cn.iocoder.yudao.module.pms.cutover.service.checklist.port;

public interface CutoverChecklistFilePort {

    FileFact lockAndRevalidate(Long tenantId, Long actorId, Long projectId, Long checklistItemId,
                               long expectedScopeVersion, FileHandle handle);

    record FileHandle(Long artifactId, Integer versionNo, String referenceKey,
                      FileFactVersion fileFactVersion, Long scopeVersion) {
    }

    record FileFact(Long artifactId, Integer versionNo, String referenceKey,
                    FileFactVersion fileFactVersion, Long scopeVersion, String sha256) {
    }

    record FileFactVersion(Integer artifactVersion, Integer referenceVersion,
                           Integer availabilityVersion) {
    }
}
