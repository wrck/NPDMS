package cn.iocoder.yudao.module.pms.cutover.service.taskv2.command;

import java.time.LocalDateTime;
import java.util.List;

public record CreateCutoverTaskCommand(
        Long tenantId, Long actorId, String idempotencyKey, String correlationId,
        String intakeSourceType, Long projectId, List<String> serialNumbers,
        String configurationCode, String taskName, String background, String cutoverType, String networkMode,
        LocalDateTime scheduledTime, String sourceSystem, String sourceBusinessNo,
        String businessEventId) {
    public CreateCutoverTaskCommand {
        serialNumbers = serialNumbers == null ? null : List.copyOf(serialNumbers);
    }
}
