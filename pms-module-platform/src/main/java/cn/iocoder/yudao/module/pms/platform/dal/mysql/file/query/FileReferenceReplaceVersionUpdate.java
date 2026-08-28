package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileReferenceReplaceVersionUpdate(
        Long tenantId,
        Long referenceId,
        Integer expectedVersion,
        Long artifactId,
        Integer fileVersionNo,
        Long scopeVersion,
        String sensitivityCode) {
}
