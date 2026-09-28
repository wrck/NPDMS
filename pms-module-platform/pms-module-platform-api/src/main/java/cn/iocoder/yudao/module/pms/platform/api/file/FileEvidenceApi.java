package cn.iocoder.yudao.module.pms.platform.api.file;

/** Internal rule fact only. Does not grant file access, expose content, or replace user authorization. */
public interface FileEvidenceApi {
    /** Internal metadata only. Owner project identity must be independently resolved before use. */
    default Document inspectDocument(Long tenantId, Long referenceId) { return null; }

    /**
     * 按工件版本反查引用文档（引用≈工件版本 1:1）：业务事件只携带 artifactId+versionNo
     * （如交付投影），登记统一材料时由此定位文件引用身份。
     */
    default Document inspectDocumentByArtifact(Long tenantId, Long artifactId, Integer versionNo) { return null; }
    record Document(Long referenceId, String ownerContext, String objectType, String objectId,
                    String purposeCode, String referenceKey, Long artifactId, Integer versionNo,
                    String sha256, String name, boolean available) { }
    Fact lockAndRevalidate(Query query);
    record Query(Long tenantId, Long artifactId, Integer versionNo, String ownerContext, String objectType,
                 String objectId, String purposeCode, String referenceKey, String sha256) { }
    record Fact(boolean valid, String reason, Integer artifactVersion, Integer availabilityVersion, Integer referenceVersion) { }
}
