package cn.iocoder.yudao.module.pms.integration.api.deviceops;

/** Browser login stays with NPDMS. Tickets authorize one local operation, never an agent identity. */
public interface DeviceOpsDesktopApi {
    Ticket register(Registration command);
    Ticket access(Access command);

    record Registration(Long tenantId, Long actorId, String projectId, String platformTaskId) { }
    record Access(Long tenantId, Long actorId, String projectId, String operation, String platformTaskId,
                  ConnectionScope connectionScope) { }
    record ConnectionScope(String deviceId, String protocol) { }
    record Ticket(String value, Long tenantId) {
        @Override public String toString() { return "DesktopTicket[redacted]"; }
    }
}
