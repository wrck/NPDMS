package cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query;

import java.time.LocalDateTime;

/** Only normal adopted records can identify the current predecessor; legacy chronology is not inferred. */
public record CurrentSoftwareRecordQuery(Long tenantId, Long deviceId, String requestKey,
        String dataSource, LocalDateTime recordedAt) {}
