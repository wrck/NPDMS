package cn.iocoder.yudao.module.pms.cutover.api.task.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 可信内部来源创建割接任务的严格判别命令。 */
public record CutoverTaskIntakeCommand(
        String sourceType,
        Long projectId,
        List<String> serialNumbers,
        Long handlingEngineerUserId,
        String configurationCode,
        String taskName,
        String background,
        String cutoverType,
        String networkMode,
        LocalDateTime scheduledTime,
        String correlationId,
        String sourceSystem,
        String sourceBusinessNo,
        String businessEventId) {

    public CutoverTaskIntakeCommand {
        serialNumbers = serialNumbers == null ? null : List.copyOf(serialNumbers);
    }
}
