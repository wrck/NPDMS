package cn.iocoder.yudao.module.pms.platform.api.file;

/** Internal rule fact only. Does not grant file access, expose content, or replace user authorization. */
public interface FileEvidenceApi {
    /** Internal metadata only. Owner project identity must be independently resolved before use. */
    default Document inspectDocument(Long tenantId, Long referenceId) { return null; }
    record Document(Long referenceId, String ownerContext, String objectType, String objectId,
                    String purposeCode, String referenceKey, Long artifactId, Integer versionNo,
                    String sha256, String name, boolean available) { }
    Fact lockAndRevalidate(Query query);
    record Query(Long tenantId, Long artifactId, Integer versionNo, String ownerContext, String objectType,
                 String objectId, String purposeCode, String referenceKey, String sha256) { }
    record Fact(boolean valid, String reason, Integer artifactVersion, Integer availabilityVersion, Integer referenceVersion) { }
}
