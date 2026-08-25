package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.time.LocalDateTime;

public record TaskProgressUpdate(Long tenantId, Long projectId, Long projectTaskId,
                                 Integer expectedVersion, Integer progress,
                                 LocalDateTime occurredAt, String updater) {
}
