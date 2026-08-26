package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileUploadSessionArtifactBindingQuery(
        Long tenantId, Long artifactId, String ownerContext,
        String objectType, String objectId, String purposeCode,
        String referenceKey) {
}
