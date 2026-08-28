package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileArtifactActivationUpdate(
        Long tenantId,
        Long artifactId,
        Integer expectedVersion) {
}
