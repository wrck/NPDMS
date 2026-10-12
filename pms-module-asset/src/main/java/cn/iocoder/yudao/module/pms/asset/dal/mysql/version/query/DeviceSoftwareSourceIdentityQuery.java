package cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query;
public record DeviceSoftwareSourceIdentityQuery(Long tenantId, Long projectId, String sourceSystem,
        String sourceKey, String sourceChecksum) {}
