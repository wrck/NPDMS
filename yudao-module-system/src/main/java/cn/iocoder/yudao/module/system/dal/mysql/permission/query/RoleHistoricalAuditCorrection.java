package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import java.time.LocalDateTime;

public record RoleHistoricalAuditCorrection(Long tenantId, Long roleId, String code,
        String expectedCreator, String expectedUpdater, String restoredCreator, String restoredUpdater,
        LocalDateTime updateTime) {}
