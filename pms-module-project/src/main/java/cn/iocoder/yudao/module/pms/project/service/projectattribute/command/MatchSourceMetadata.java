package cn.iocoder.yudao.module.pms.project.service.projectattribute.command;

import java.time.LocalDateTime;

/** 一次来源属性判定实际消费的来源与映射证据。 */
public record MatchSourceMetadata(
        String sourceOwner,
        String sourceSystem,
        String sourceKey,
        String sourceEventId,
        String sourceVersion,
        LocalDateTime sourceOccurredAt,
        String sourceValueDigest,
        String mappingVersion) {
}
