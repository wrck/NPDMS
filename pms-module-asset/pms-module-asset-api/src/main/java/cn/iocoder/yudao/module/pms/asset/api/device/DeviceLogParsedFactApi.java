package cn.iocoder.yudao.module.pms.asset.api.device;

import java.time.LocalDateTime;

/** AST owns serial association and the current version derived from immutable log evidence. */
public interface DeviceLogParsedFactApi {
    Outcome record(ParsedLog command);

    record ParsedLog(Long tenantId, Long actorId, Long projectId, String requestKey,
                     String logKey, LocalDateTime logReceivedAt, String parserTaskId,
                     String parserVersion, Long sourceFileId, String serialNumber,
                     String conpVersion, String bootVersion, String cpldVersion, String pcbVersion, Long logVersion) {
        public ParsedLog(Long tenantId, Long actorId, Long projectId, String requestKey,
                         String logKey, LocalDateTime logReceivedAt, String parserTaskId,
                         String parserVersion, Long sourceFileId, String serialNumber,
                         String conpVersion, String bootVersion, String cpldVersion, String pcbVersion) {
            this(tenantId, actorId, projectId, requestKey, logKey, logReceivedAt, parserTaskId,
                    parserVersion, sourceFileId, serialNumber, conpVersion, bootVersion, cpldVersion, pcbVersion, null);
        }
    }
    record Outcome(String status, Long deviceId, String serialNumber, String versionStatus, String message) { }
}
