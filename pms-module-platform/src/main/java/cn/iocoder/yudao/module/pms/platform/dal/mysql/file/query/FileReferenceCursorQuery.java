package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

import java.time.LocalDateTime;

public record FileReferenceCursorQuery(
        Long tenantId,
        String ownerContext,
        String objectType,
        String objectId,
        LocalDateTime afterCreateTime,
        Long afterId,
        int limit) {
}
