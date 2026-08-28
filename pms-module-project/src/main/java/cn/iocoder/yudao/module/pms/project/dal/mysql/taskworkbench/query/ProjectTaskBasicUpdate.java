package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.time.LocalDateTime;
import java.util.Set;

public record ProjectTaskBasicUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                     String name, String businessLevelCode,
                                     LocalDateTime planStartTime, LocalDateTime planEndTime,
                                     Integer priority, Integer sortOrder, String description,
                                     String updater, Set<String> submittedFields) {
}
