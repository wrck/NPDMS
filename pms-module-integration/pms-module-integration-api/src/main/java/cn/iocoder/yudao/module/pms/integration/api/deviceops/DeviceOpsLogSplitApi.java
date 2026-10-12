package cn.iocoder.yudao.module.pms.integration.api.deviceops;
import java.util.List;

/** DAC owns content splitting/redaction; callers own project and device association authorization. */
public interface DeviceOpsLogSplitApi {
    Result split(Command command);
    record Command(Long tenantId, Long projectId, String format, String content) { }
    record DeviceLog(String serialNumber, String content) { }
    record Result(String sanitizedSource, int redactedValueCount, List<DeviceLog> devices) {
        public Result { devices = List.copyOf(devices); }
    }
    final class Rejected extends RuntimeException {
        public Rejected(String reason) { super(reason); }
    }
}
