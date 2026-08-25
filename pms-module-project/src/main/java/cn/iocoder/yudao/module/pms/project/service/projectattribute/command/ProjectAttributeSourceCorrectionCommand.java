package cn.iocoder.yudao.module.pms.project.service.projectattribute.command;

import java.time.LocalDateTime;

/** 已完成项目定位的受信任来源属性修正命令。 */
public record ProjectAttributeSourceCorrectionCommand(
        Long projectId,
        Integer expectedVersion,
        String signingMethod,
        String implementationMode,
        String majorProjectLevel,
        String sourceOwner,
        String sourceSystem,
        String sourceKey,
        String sourceEventId,
        String sourceVersion,
        LocalDateTime sourceOccurredAt,
        String sourceValueDigest,
        String mappingVersion,
        String correctionReason,
        String idempotencyKey,
        String requestDigest,
        String serviceIdentity) {
}
