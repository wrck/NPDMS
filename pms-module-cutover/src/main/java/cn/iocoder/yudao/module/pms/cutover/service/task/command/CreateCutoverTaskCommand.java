package cn.iocoder.yudao.module.pms.cutover.service.task.command;

import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverDeviceScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectContextPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverReadinessPort;

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
