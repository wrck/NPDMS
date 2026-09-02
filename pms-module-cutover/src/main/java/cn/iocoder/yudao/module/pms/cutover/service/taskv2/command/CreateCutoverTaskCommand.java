package cn.iocoder.yudao.module.pms.cutover.service.taskv2.command;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverReadinessPort;

import java.time.LocalDateTime;
import java.util.List;

public record CreateCutoverTaskCommand(
        Long tenantId, Long actorId, String idempotencyKey, String correlationId,
        String intakeSourceType, Long projectId, List<String> serialNumbers,
        String configurationCode, String taskName, String background, String cutoverType, String networkMode,
        LocalDateTime scheduledTime, String sourceSystem, String sourceBusinessNo,
        String businessEventId, ExpectedCreateContext expectedContext) {
    public CreateCutoverTaskCommand {
        serialNumbers = serialNumbers == null ? null : List.copyOf(serialNumbers);
    }

    public record ExpectedCreateContext(CutoverProjectContextPort.ProjectContextFact project,
                                        List<CutoverDeviceScopePort.DeviceFact> devices,
                                        CutoverCustomerLevelPort.CustomerLevelFact customer,
                                        CutoverReadinessPort.ReadinessFact readiness) {
        public ExpectedCreateContext {
            devices = devices == null ? null : List.copyOf(devices);
        }
    }
}
