package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record FileVersionCursorQuery(
        Long tenantId,
        Long artifactId,
        Integer afterVersionNo,
        Long afterId,
        int limit) {
}
