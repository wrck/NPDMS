package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query;

import java.time.LocalDateTime;

public record CutoverExternalDeviceUpdate(Long tenantId, Long checklistId, Long deviceId,
        String productModel, String currentSoftwareVersion, String targetSoftwareVersion,
        Long actorId, LocalDateTime time) { }
